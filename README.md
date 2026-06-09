# Card Transaction System

Processamento de transações de cartão de crédito em arquitetura **serverless de custo zero** (exceto Bedrock): autorização **assíncrona** com saga distribuída (AWS Step Functions), análise de fraude por agente Bedrock (ReAct), observability **OpenTelemetry → New Relic** e logging estruturado em JSON. Construído com **Arquitetura Hexagonal + DDD**, Java 25 e Spring Boot 4, rodando em AWS Lambda (Function URL) + DynamoDB.

> Documentos complementares: [`diagrama.html`](./diagrama.html) (C4 container), [`DEEP_DIVE.md`](./DEEP_DIVE.md) (decisões de arquitetura/custo), [`OBSERVABILITY.md`](./OBSERVABILITY.md) (plano de observability), [`DEPLOYMENT.md`](./DEPLOYMENT.md), [`LOCALSTACK.md`](./LOCALSTACK.md).

---

## Visão geral da arquitetura

A autorização é **assíncrona** para não pagar Lambda ociosa:

1. `POST /process` → o `TransactionOrchestrator` verifica idempotência e fraud score em cache, salva a transação como `PENDING`, injeta o `traceparent`, dispara o Step Functions (`StartExecution`) e responde **`202 Accepted`** com `correlationId`.
2. A saga (Step Functions **STANDARD**, em background) executa: `ValidateBusinessRules` → `AnalyzeFraud` (Bedrock) → `UpdateStatus` (decide aprovar/rejeitar e grava idempotência). Qualquer erro cai em `RollbackCompensation` (Catch).
3. O cliente consulta o resultado em `GET /status/{correlationId}`.

Premissas de custo (conta com free tier de 12 meses expirado, mas *always-free* intacto): só serviços sempre-gratuitos (Lambda, DynamoDB, Step Functions Standard, SSM, 100GB/mês de egress). **Único item pago: Bedrock** — e no perfil local ele é substituído por Ollama/Mistral via feature flag.

> ⚠️ **Limitação conhecida:** o `GET /status/{correlationId}` existe como controller REST (funciona local), mas **ainda não está publicado na AWS** — só há Function URL para `process`, `login` e `token-exchange`. Para o fluxo assíncrono ponta-a-ponta em produção, falta adicionar uma `statusFunction` + Lambda URL. Veja o Roadmap.

---

## Quick Start

### Pré-requisitos
- Java 25
- Docker & Docker Compose
- Maven (wrapper incluído)

### Setup local
```bash
git clone <repo>
cd card-transaction-system

./mvnw clean package                 # build
docker-compose up -d                 # LocalStack (AWS) + Ollama (fraude local)
./mvnw clean test                    # testes (108 testes)
open target/site/jacoco/index.html   # coverage
```

### Rodar a aplicação
```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"
```

### Exercitar a API (local)
```bash
# 1) Login → JWT
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"john","password":"secret"}'

# 2) Iniciar transação → 202 + correlationId
curl -X POST http://localhost:8080/process \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <JWT>" \
  -d '{
    "transactionId": "TXN-001",
    "uuidTransaction": "550e8400-e29b-41d4-a716-446655440000",
    "cardDataRequest": { "number": "4111111111111111", "cvv": "123", "name": "John Doe", "brand": "VISA" },
    "amount": 500.00,
    "installments": 3
  }'

# 3) Consultar status
curl http://localhost:8080/status/550e8400-e29b-41d4-a716-446655440000
```

---

## API

### `POST /auth/login`
```jsonc
// request (LoginRequest)
{ "username": "john", "password": "secret" }
// response 200 (JwtResponse)
{ "token": "eyJhbGc..." }
```

### `POST /process`
```jsonc
// request (CardTransactionRequest) — requer JWT
{
  "transactionId": "TXN-001",
  "uuidTransaction": "550e8400-e29b-41d4-a716-446655440000",
  "cardDataRequest": { "number": "4111111111111111", "cvv": "123", "name": "John Doe", "brand": "VISA" },
  "amount": 500.00,
  "installments": 3
}
// response 202 Accepted (TransactionInitiatedResponse)
{ "transactionId": "TXN-001", "correlationId": "550e8400-e29b-41d4-a716-446655440000" }
```

### `GET /status/{correlationId}`
```jsonc
// response 200 (TransactionStatusResponse) | 404 se não encontrado
{ "correlationId": "550e8400-...", "status": "APPROVED", "reason": null }
```
`status` ∈ `PENDING | APPROVED | REJECTED`.

### Spring Cloud Functions (Lambda)
`tokenExchange` · `loginFunction` · `processTransactionFunction` · `validateBusinessRulesFunction` · `validateTransactionFunction` · `fraudAnalysisFunction` · `updateStatusFunction` · `compensationFunction`.

Cada function é embrulhada por um wrapper que faz **force flush** de traces e métricas antes do *freeze* do Lambda.

---

## Estrutura (Hexagonal + DDD)

```
src/main/java/com/empresa/cardtransactionsystem/
├── domain/                         # núcleo puro (sem frameworks)
│   ├── model/                      # Value Objects + agregados (SagaPayload, CardToken, FraudScore...)
│   ├── service/                    # CardValidationService (registrado como bean em DomainConfig)
│   └── ports/{input,output}/       # interfaces (use cases / saídas)
├── application/
│   ├── orchestrator/               # TransactionOrchestrator (único; assíncrono via SagaStarterPort)
│   └── usecase/                    # fraude, regras, idempotência, status, compensação
├── adapters/
│   ├── inbound/
│   │   ├── rest/                   # AuthController, ProcessController, StatusController
│   │   ├── function/               # Spring Cloud Functions (+ force flush, restore traceparent)
│   │   └── logging/                # CorrelationIdFilter, StructuredLogger (JSON)
│   └── outbound/
│       ├── dynamodb/               # adapters DynamoDB (instância única)
│       ├── saga/                   # StepFunctionsSagaStarterAdapter
│       ├── bedrock/ · ollama/      # FraudAnalysisPort (feature flag)
│       ├── lambda/                 # JwtGenerator, token exchange
│       └── observability/          # TraceparentExtractor, TransactionMetrics, FlushableOtlpMeterRegistry
└── config/                         # DynamoDB, SQS(local), SFN, SSM, Security, Observability (Local/Prod)
```

### Padrões
- **Hexagonal:** domínio isolado de frameworks; ports + adapters; troca local ↔ AWS por profile.
- **DDD:** Value Objects imutáveis com validação no compact constructor; sem modelo anêmico.
- **Saga assíncrona:** orquestrador único + `SagaStarterPort`; saga no Step Functions Standard; compensação por `Catch`.
- **TDD:** 108 testes (unidade + integração com LocalStack/Testcontainers).

---

## Stack técnico

| Componente | Propósito |
|-----------|----------|
| Java 25 / Spring Boot 4.0.6 | linguagem / framework |
| AWS Lambda (Function URL) | runtime serverless, **arm64 + SnapStart** |
| AWS Step Functions (STANDARD) | orquestração da saga (assíncrona) |
| Amazon DynamoDB (on-demand) | persistência (instância única) |
| AWS Bedrock | agente de fraude (ReAct) — **único item pago** |
| Ollama / Mistral | fraude no local (feature flag `fraud-analysis.provider`) |
| AWS SSM Parameter Store | segredos (`jwt_secret`, `nr-license-key`) |
| Spring Cloud Function | abstração serverless |
| Micrometer Observation + bridge OTel + OTLP | tracing/métricas vendor-neutral |
| New Relic (free) / Jaeger (local) | backend de observability |
| Logback + Logstash encoder | logging JSON estruturado |
| Testcontainers + LocalStack | testes de integração |
| JaCoCo | cobertura |

---

## Observability

Instrumentação única com **OpenTelemetry via Micrometer Observation** (sem javaagent, substitui o antigo AspectJ). O backend é só configuração por profile:

- **Local (`local`):** `LoggingSpanExporter` (console) por padrão; Jaeger opt-in (`management.otlp.tracing.endpoint`). Zero custo, offline.
- **AWS (`!local`):** OTLP → New Relic (`otlp.nr-data.net`), `api-key` lida do **SSM**. Traces + métricas, com dashboard e 4 alertas provisionados via `newrelic.tf`.

Detalhes críticos tratados: **force flush** antes do freeze e propagação do **`traceparent` W3C** dentro do `SagaPayload` (trace único `/process → SFN → UpdateStatus`). Logs em JSON com `trace_id`/`span_id` no MDC. Plano completo em [`OBSERVABILITY.md`](./OBSERVABILITY.md).

---

## Custo (resumo)

| Item | Custo |
|---|---|
| Lambda, DynamoDB, Step Functions (Standard ≤4000 transições/mês), SQS local | $0 (always-free) |
| New Relic | $0 (free: 100 GB/mês, 1 usuário) |
| Egress Lambda → New Relic | $0 (dentro dos 100 GB/mês de DTO grátis) |
| Bedrock | **pago** (no local: Ollama, grátis) |

Cuidados: não colocar Lambda em VPC com NAT Gateway; manter log em `INFO`; aplicar sampling se o volume crescer.

---

## Configuração (profiles)

| | `local` | `!local` (AWS) |
|---|---|---|
| Fraude | Ollama/Mistral | Bedrock |
| DynamoDB | LocalStack (`:4566`) | DynamoDB nativo |
| Saga | Step Functions (LocalStack) | Step Functions Standard |
| Observability | console / Jaeger | OTLP → New Relic |
| Segredos | env/local | SSM Parameter Store |

---

## Segurança

- JWT validado por `JwtAuthenticationFilter` + `SecurityFilterConfig`.
- Segredos (`jwt_secret`, `nr-license-key`) no **SSM Parameter Store** (nunca em tfvars).
- **PCI:** o `SagaPayload` trafega apenas `cardToken` (sem PAN/CVV); `include_execution_data=false` no Step Functions.
- CORS restrito (`var.allowed_origins`); Function URL com validação de JWT na aplicação.

---

## Desenvolvimento

```bash
# Teste específico
./mvnw test -Dtest=TransactionOrchestratorTest

# Coverage report
./mvnw clean test jacoco:report

# Build para Lambda
./mvnw clean package -DskipTests
```

**Checklist antes de commitar:** `./mvnw clean test` verde · domínio sem anotações Spring · adapters não vazam para o domínio · sem comentários desnecessários (Clean Code, ver [`CLAUDE.md`](AGENTS.md)).

---

## Deployment (Terraform)

```bash
cd terraform
terraform init
terraform apply   # cria Lambdas (arm64+SnapStart), Function URLs, DynamoDB,
                  # Step Functions, SSM, e recursos New Relic (dashboard/alertas)
```
Variáveis sensíveis (`jwt_secret`, `nr_license_key`, chaves New Relic) via `terraform.tfvars` (não versionar). Detalhes em [`DEPLOYMENT.md`](./DEPLOYMENT.md).

---

## Roadmap

- [ ] **Publicar `statusFunction` + Lambda URL** (fechar o fluxo assíncrono em produção)
- [ ] Smoke test de deploy (1 aprovada + 1 rejeitada, trace único no New Relic, alertas armados)
- [ ] Rotação de `nr-license-key` sem republish do snapshot (SnapStart)
- [ ] DLQ/redrive e alarmes adicionais
- [ ] Rate limiting por usuário

---

## Troubleshooting

```bash
# LocalStack
docker-compose ps && docker-compose logs localstack
docker-compose restart localstack

# Resetar ambiente
docker-compose down -v && docker-compose up -d

# DynamoDB local
aws dynamodb delete-table --table-name card-transactions --endpoint-url http://localhost:4566
```

---

**Última atualização:** 2026-06-06 · **Versão:** 0.0.1-SNAPSHOT · **Status:** arquitetura assíncrona + observability implementadas (108 testes verdes)
**Contato:** reinaldo.java@gmail.com · **Licença:** MIT
