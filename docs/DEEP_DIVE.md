# Deep Dive — Card Transaction System

> Java 25 · Spring Boot 4 · AWS Serverless · Hexagonal · DDD · Premissa: **custo zero (exceto Bedrock)**
> Data: 2026-06-06

## Contexto de custo (atualizado)

- Free tier de **12 meses expirado**, mas isso impacta pouco: a stack usa só serviços **"always free"** (não expiram):
  Lambda (1M req + 400k GB-s/mês), DynamoDB (25GB + 200M req/mês), SQS (1M req/mês),
  Step Functions **Standard** (4.000 transições/mês). **Express NÃO tem free tier.**
- Em volume de demo/portfólio a stack inteira fica **~$0**, exceto Bedrock.
- **Feature flag** `fraud-analysis.provider` (BEDROCK | OLLAMA) via `FraudAnalysisAdapterFactory`:
  dev local roda Ollama/Mistral (grátis), prod usa Bedrock. Strategy bem aplicado.

---

## 1. O que está bom

### Arquitetura hexagonal
- Separação `domain` / `domain.ports` / `adapters` correta. O domínio não importa AWS SDK.
- Ports de saída coesos e bem nomeados: `TransactionRepositoryPort`, `CachePort`, `FraudAnalysisPort`, `ClientProfilePort`, `TransactionHistoryPort`, `TransactionQueuePort`, `TokenExchangePort`, `UserRepositoryPort`.

### DDD sem modelo anêmico
- Value Objects ricos com validação no compact constructor: `CardNumber`, `Cvv`, `FraudScore`, `CardToken`, `CvvHash`.
- Factories expressivas: `CardTransaction.newPending`, `TransactionResult.approved/rejected/timeout`.
- Comportamento no agregado: `ClientProfile.availableCredit()` / `hasAvailableCredit()`.
- `ValidateBusinessRulesService` calcula juros compostos com `BigDecimal` + `MathContext.DECIMAL64` e `RoundingMode.HALF_UP`.

### Java 25 / Spring Boot 4
- `StructuredTaskScope` com `Joiner.anySuccessfulResultOrThrow` + timeout.
- Switch expressions, text blocks, records, Jackson 3 (`tools.jackson`).

### Agente de fraude (Bedrock)
- Loop ReAct via Converse API + tool-use (`get_client_history`, `get_merchant_profile`) bem implementado.
- Cache de score evita reanálise (reduz custo Bedrock).

### Custo zero na infra base
- DynamoDB `PAY_PER_REQUEST`.
- Lambda Function URL (sem API Gateway).
- Cache com TTL, SQS FIFO com dedup.
- Terraform completo; testes (TDD) para as 8 Spring Cloud Functions.

---

## 2. O que precisa melhorar

Ordenado por impacto. Tags: **[CUSTO]** · **[SEGURANÇA]** · **[DESIGN]**

### 1. [DESIGN] Três orchestrators quase idênticos
`TransactionOrchestrator` (sem `@Profile`), `LocalTransactionOrchestrator` (`@Profile local`) e
`AwsTransactionOrchestrator` (`@Profile !local`) têm ~90% de código duplicado. O sem-profile é
sempre bean → conflito / dead code, mascarado por `allow-bean-definition-overriding: true`.
**Ação:** consolidar em **um** orchestrator + port `SagaStarterPort` com 2 adapters
(Step Functions / use case local).

### 2. [CUSTO] Polling síncrono dentro da Lambda — desperdício de GB-s
O orchestrator bloqueia até 30s chamando `findStatus` no DynamoDB a cada 500ms enquanto o
Step Functions roda assíncrono. Em volume de demo isso fica dentro do always-free, mas **queima
o budget de 400k GB-s/mês** com Lambda ociosa (512MB × ~Ns × N tx) e é inelegante.
**Ação recomendada:** **assíncrono** — `202 + transactionId` e GET `/status/{id}`. O orchestrator
retorna em ~ms (mínimo GB-s) e a saga roda em background.

### 3. [CUSTO] Step Functions: MANTER `STANDARD` (não migrar para Express)
Correção da análise anterior: com free tier de 12 meses expirado, **Standard** continua dando
4.000 transições/mês grátis (always-free) ≈ 800 sagas/mês. **Express NÃO tem free tier** → cobra
desde a 1ª requisição. Como `StartSyncExecution` (síncrono) só existe no Express, o caminho de
**custo zero é Standard + assíncrono** (item 2). Express só se UX síncrona for requisito rígido.

### 4. [SEGURANÇA/PCI] PAN e CVV trafegando serializados
`SqsTransactionQueueAdapter` e `AwsTransactionOrchestrator` fazem
`objectMapper.writeValueAsString(transaction)` no `CardTransaction` inteiro → inclui número do
cartão e CVV. Pior: Step Functions com `include_execution_data = true` envia o input ao
**CloudWatch Logs**. PCI também proíbe armazenar CVV mesmo com hash (`CvvHash` é red flag).
**Ação:** trafegar só `cardToken` + `correlationId`; desligar `include_execution_data`; remover `CvvHash`.

### 5. [SEGURANÇA] Endpoint público + segredos commitados
Function URL com `authorization_type = NONE` + CORS `allow_origins ["*"]` com
`allow_credentials = true`; sem filtro de validação de JWT visível no fluxo `/process`.
`jwt_secret` está no `terraform.tfvars` e há secret default no `application.yml`.
**Ação:** validar JWT (Function URL `AWS_IAM` ou filtro próprio), restringir CORS, mover segredos
para SSM Parameter Store / Secrets Manager (Parameter Store Standard = grátis).

### 6. [DESIGN] Vazamento de framework no domínio
`domain/service/CardValidationService` tem `@Service` — viola "domínio puro" do CLAUDE.md.
**Ação:** registrar como bean num `@Configuration` da camada application.

### 7. [CUSTO/perf] Cold start não otimizado
Lambda `x86_64`, 512MB, **sem SnapStart e sem GraalVM native**. Spring Boot 4 na JVM tem cold
start pesado (~5-10s). **Ação (ganhos grátis):** `arm64` (Graviton, ~20% mais barato) + SnapStart.

### 8. [DESIGN] `new ObjectMapper()` em adapters
Instanciado em `SqsTransactionQueueAdapter` e `AwsTransactionOrchestrator` em vez de bean
compartilhado. **Ação:** injetar bean único.
(Correção: `RestTemplateConfig` **não** é morto — o `OllamaFraudAnalysisAdapter` o usa.)

### 10. [DESIGN] Feature flag de fraude — refinar
`FraudAnalysisAdapterFactory` lê o campo público `properties.provider` em vez do getter. Opção
mais idiomática: `@ConditionalOnProperty(name="fraud-analysis.provider", havingValue="OLLAMA")`
em cada adapter, eliminando o factory. Manter o flag (dev grátis com Ollama).

### 9. [DESIGN] Idempotência só guarda APPROVED
`IdempotencyService.store` ignora REJECTED → retry de transação rejeitada re-executa a saga
inteira (custo + risco). **Ação:** persistir resultado final independente do status (com TTL).

---

## 3. Desenho-alvo recomendado (custo zero real)

```
Cliente
  │  POST /process  (JWT)
  ▼
Lambda Function URL  ── orchestrator (1 só, sem duplicação)
  │  idempotência (cache) ──► hit? retorna
  │  tokeniza cartão ──► só cardToken+correlationId daqui pra frente
  │  StartExecution (Step Functions STANDARD)  ──► retorna 202 + transactionId  (~ms)
  ▼
Step Functions STANDARD (background, ≤4000 transições/mês = grátis)
  Validate Rules ──► Fraud (Bedrock|Ollama) ──► Decision ──► Update Status (DynamoDB)

Cliente
  │  GET /status/{transactionId}
  ▼
Lambda Function URL ──► DynamoDB getItem ──► PENDING | APPROVED | REJECTED
```

Mudanças-chave vs. atual:
- **Assíncrono** (`202 + GET status`): elimina Lambda ociosa e o `StructuredTaskScope` de polling.
- **Mantém Step Functions STANDARD** (grátis ≤4000 transições; Express cobraria desde req 1).
- **Remove SQS** do caminho AWS (mantém só no perfil local, se desejado).
- **Sem PAN/CVV** no payload da saga; `include_execution_data = false`.
- `arm64` + SnapStart; manter feature flag Bedrock/Ollama.

Alternativa (só se UX síncrona for requisito rígido): Express + `StartSyncExecution` — remove
polling porém sai do free tier de Step Functions (custo pequeno desde a 1ª requisição).

---

## 4. Backlog priorizado

> Itens marcados com ✅ foram implementados.

| # | Item | Tag | Status |
|---|------|-----|--------|
| 1 | Consolidar 3 orchestrators + `SagaStarterPort` | DESIGN | ✅ |
| 2 | Assíncrono (202 + GET status); remover polling; manter SFN Standard | CUSTO | ✅ |
| 3 | Tirar PAN/CVV do tráfego; `include_execution_data=false`; remover CvvHash | SEGURANÇA | M |
| 4 | arm64 + SnapStart | CUSTO | ✅ |
| 5 | JWT no endpoint + CORS restrito + segredos no Parameter Store | SEGURANÇA | ✅ |
| 6 | Remover `@Service` do domínio | DESIGN | S |
| 7 | ObjectMapper como bean; remover RestTemplate morto | DESIGN | S |
| 8 | Idempotência também para REJECTED | DESIGN | S |
| 9 | Remover `allow-bean-definition-overriding` após item 1 | DESIGN | ✅ |
| 10 | OpenSearch → PostgresAuditAdapter; profile groups corretos | DESIGN | ✅ |
| 11 | Callback/webhook por HMAC-SHA256 (`callbackUrl` em `SagaPayload`) | DESIGN | ✅ |
| 12 | `getStatusFunction` Lambda + Terraform | DESIGN | ✅ |
