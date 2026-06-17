# Card Transaction System

Processamento de transações de cartão de crédito em arquitetura **serverless de custo zero** (exceto Bedrock): autorização **assíncrona** com saga distribuída (AWS Step Functions), análise de fraude por agente Bedrock (ReAct), observability **OpenTelemetry → New Relic** e logging estruturado em JSON. Construído com **Arquitetura Hexagonal + DDD**, Java 25 e Spring Boot 4, rodando em AWS Lambda (Function URL) + DynamoDB.

> Documentos complementares: [`docs/diagrama.html`](./docs/diagrama.html) (C4 container), [`docs/fluxo-transacao.html`](./docs/fluxo-transacao.html) (fluxo ponta a ponta da transação), [`docs/ARCHITECTURE.md`](./docs/ARCHITECTURE.md), [`docs/DEEP_DIVE.md`](./docs/DEEP_DIVE.md) (decisões de arquitetura/custo), [`docs/LOCAL_DEV.md`](./docs/LOCAL_DEV.md) (stack local-rich), [`docs/OBSERVABILITY.md`](./docs/OBSERVABILITY.md) (plano de observability), [`docs/CICD.md`](./docs/CICD.md) (GitHub Actions + OIDC), [`docs/DEPLOYMENT.md`](./docs/DEPLOYMENT.md), [`docs/LOCALSTACK.md`](./docs/LOCALSTACK.md).

---

## Visão geral da arquitetura

A autorização é **assíncrona** para não pagar Lambda ociosa:

1. `POST /process` → o `TransactionOrchestrator` verifica idempotência e fraud score em cache, salva a transação como `PENDING`, injeta o `traceparent`, dispara o Step Functions (`StartExecution`) e responde **`202 Accepted`** com `correlationId`.
2. A saga (Step Functions **STANDARD**, em background) executa: `ValidateBusinessRules` → `AnalyzeFraud` (Bedrock) → `UpdateStatus` (decide aprovar/rejeitar e grava idempotência). Qualquer erro cai em `RollbackCompensation` (Catch).
3. O cliente consulta o resultado em `GET /status/{correlationId}`.

Premissas de custo (conta com free tier de 12 meses expirado, mas *always-free* intacto): só serviços sempre-gratuitos (Lambda, DynamoDB, Step Functions Standard, SSM, 100GB/mês de egress). **Único item pago: Bedrock**, e no perfil local ele é substituído por Ollama/Mistral via feature flag.

> ℹ️ **Retorno ao cliente:** o resultado pode ser consultado por **polling** em `GET /status/{correlationId}` (publicado na AWS via `getStatusFunction` + Lambda URL) **ou** recebido por **webhook**, se a transação enviar `callbackUrl`, o `UpdateStatus` faz um `POST` assinado (HMAC) com o resultado.

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
./mvnw clean test                    # suíte de testes (unit + integração via Testcontainers)
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
# locationCode é opcional: omitir para localização aleatória
curl -X POST http://localhost:8080/process \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <JWT>" \
  -d '{
    "transactionId": "TXN-001",
    "uuidTransaction": "550e8400-e29b-41d4-a716-446655440000",
    "cardDataRequest": { "number": "4111111111111111", "cvv": "123", "name": "John Doe", "brand": "VISA" },
    "amount": 500.00,
    "installments": 3,
    "locationCode": "SAO_PAULO_CENTRO"
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
// request (CardTransactionRequest), requer JWT
{
  "transactionId": "TXN-001",
  "uuidTransaction": "550e8400-e29b-41d4-a716-446655440000",
  "cardDataRequest": { "number": "4111111111111111", "cvv": "123", "name": "John Doe", "brand": "VISA" },
  "amount": 500.00,
  "installments": 3,
  "locationCode": "SAO_PAULO_CENTRO"   // opcional, omitir para localização aleatória
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

### Audit API (somente `local-rich` / `ledger-postgres`)
Consulta da projeção de auditoria (Kafka → Postgres). Protegida por JWT. Não existe no perfil AWS (`queue-none`, sem projeção).
```
GET /audit/card/{cardToken}?limit=50
GET /audit/status?status=APPROVED&from=2026-06-01&to=2026-06-17
```

### Spring Cloud Functions (Lambda)
`tokenExchange` · `loginFunction` · `processTransactionFunction` · `getStatusFunction` · `validateBusinessRulesFunction` · `validateTransactionFunction` · `fraudAnalysisFunction` · `updateStatusFunction` · `compensationFunction`.

Cada function é embrulhada por um wrapper que faz **force flush** de traces e métricas antes do *freeze* do Lambda.

---

## Geolocalização de Transações

### Como funciona

Cada transação pode indicar de onde ela está sendo feita via o campo `locationCode` (string). O sistema mantém uma **tabela-verdade** de 52 locais fictícios em `src/main/resources/geo/test-locations.json`, carregada em memória na inicialização via `GeoLocationRegistry`, **zero custo de infra, zero chamada de API**.

**Fluxo:**
1. Se `locationCode` for enviado e existir na tabela → usa esse local.
2. Se `locationCode` for omitido (ou `null`) → o sistema sorteia um local aleatório da tabela, tornando os testes mais realistas.
3. O `locationCode` é persistido na transação (Postgres e DynamoDB).

### Análise de fraude geográfica

O local da transação **e** o local de residência do cliente (campo `homeLocationCode` no perfil, ex: `SAO_PAULO_CENTRO`) são enviados à IA com nomes legíveis por humano:

```
transaction_location: Polo Norte, Ártico (lat=90.0000, lon=0.0000), NOTE: região inabitada, fisicamente impossível
home_location: São Paulo - Centro, Brasil (lat=-23.5505, lon=-46.6333)
```

A IA (Bedrock/Claude ou Ollama/Mistral) **raciocina** sobre a plausibilidade geográfica, aplicando penalidades crescentes:

| Situação | Penalidade |
|---|---|
| Local fisicamente impossível (Polo Norte, meio do oceano) | +40 pts |
| País muito distante do cadastro (São Paulo → Tóquio) | +30 pts |
| Continente diferente (São Paulo → Londres) | +20 pts |
| País vizinho (São Paulo → Buenos Aires) | +10 pts |
| Mesma cidade / região próxima | −10 pts |

No **modo fallback** (sem IA disponível), o `GeoDistanceCalculator` aplica Haversine: distância > 8.000 km → bloqueio automático (score 100).

### Locais disponíveis (exemplos)

| Código | Cidade | País |
|---|---|---|
| `SAO_PAULO_CENTRO` | São Paulo - Centro | Brasil |
| `SAO_PAULO_VILA_OLIMPIA` | São Paulo - Vila Olímpia | Brasil |
| `RIO_DE_JANEIRO` | Rio de Janeiro | Brasil |
| `BUENOS_AIRES` | Buenos Aires | Argentina |
| `NEW_YORK` | Nova York | EUA |
| `LONDON` | Londres | Reino Unido |
| `TOKYO` | Tóquio | Japão |
| `NORTH_POLE` | Polo Norte | Ártico |
| `ATLANTIC_OCEAN_MID` | Meio do Atlântico | n/a |

Lista completa em `src/main/resources/geo/test-locations.json`.

---

## Estrutura (Hexagonal + DDD)

```
src/main/java/com/empresa/cardtransactionsystem/
├── domain/                         # núcleo puro (sem frameworks)
│   ├── model/                      # Value Objects + agregados (SagaPayload, CardToken, FraudScore,
│   │                               #   GeoLocation, GeoRiskLevel...)
│   ├── service/                    # CardValidationService, GeoLocationRegistry, GeoDistanceCalculator
│   └── ports/{input,output}/       # interfaces (use cases / saídas)
├── application/
│   ├── orchestrator/               # TransactionOrchestrator (único; assíncrono via SagaStarterPort)
│   └── usecase/                    # fraude, regras, idempotência, status, compensação
├── adapters/
│   ├── inbound/
│   │   ├── rest/                   # AuthController, ProcessController, StatusController, AuditController
│   │   ├── function/               # Spring Cloud Functions (+ force flush, restore traceparent)
│   │   └── logging/                # CorrelationIdFilter (correlationId no MDC)
│   └── outbound/
│       ├── dynamodb/               # adapters DynamoDB (instância única)
│       ├── saga/                   # StepFunctionsSagaStarterAdapter (aws) · Temporal (local-rich)
│       ├── redis/ · kafka/ · postgres/  # cache, eventos e ledger/auditoria (local-rich)
│       ├── bedrock/ · ollama/      # FraudAnalysisPort (feature flag; ambos recebem geo context)
│       ├── lambda/                 # JwtGenerator, token exchange
│       └── observability/          # TraceparentExtractor, TransactionMetrics, FlushableOtlpMeterRegistry
└── config/                         # DynamoDB/Redis, Kafka, SFN/Temporal, SSM, SecurityConfig,
                                     #   Resilience4j, Observability (Local/Prod)

src/main/resources/
└── geo/test-locations.json         # 52 locais fictícios (carregados em memória na inicialização)
```

### Padrões
- **Hexagonal:** domínio isolado de frameworks; ports + adapters; troca local ↔ AWS por profile.
- **DDD:** Value Objects imutáveis com validação no compact constructor; sem modelo anêmico.
- **Saga assíncrona:** orquestrador único + `SagaStarterPort`; saga no Step Functions Standard; compensação por `Catch`.
- **TDD:** suíte de testes de unidade + integração (LocalStack/Testcontainers).

---

## Stack técnico

| Componente | Propósito |
|-----------|----------|
| Java 25 / Spring Boot 4.1 | linguagem / framework |
| AWS Lambda (Function URL) | runtime serverless, **arm64 + SnapStart** |
| AWS Step Functions (STANDARD) | orquestração da saga (assíncrona) |
| Amazon DynamoDB (on-demand) | persistência (instância única) |
| AWS Bedrock | agente de fraude (ReAct), **único item pago** |
| Ollama / Mistral | fraude no local (feature flag `fraud-analysis.provider`) |
| AWS SSM Parameter Store | segredos (`jwt_secret`, `nr-license-key`) |
| Spring Cloud Function | abstração serverless |
| Micrometer Observation + bridge OTel + OTLP | tracing/métricas vendor-neutral |
| New Relic (free, AWS) / Grafana `otel-lgtm` (local) | backend de observability |
| Resilience4j (circuit breaker) | resiliência dos adapters de IA (degrade) |
| Redis · Kafka/Redpanda · Temporal · PostgreSQL | stack local-rich (cache, eventos, saga, ledger) |
| Logback + Logstash encoder | logging JSON estruturado |
| Testcontainers + LocalStack | testes de integração |
| JaCoCo | cobertura |

---

## Observability

Instrumentação única com **OpenTelemetry via Micrometer Observation** (sem javaagent). O backend é só configuração por profile:

- **Local (`local`):** `LoggingSpanExporter` (console) por padrão. Zero custo, offline.
- **Local-rich (`local-rich`):** OTLP → imagem **`grafana/otel-lgtm`** (OTel Collector + Tempo + Prometheus + Loki + Grafana em `:3000`); traces, métricas e logs num backend único local.
- **AWS (`!local`):** OTLP → New Relic (`otlp.nr-data.net`), `api-key` lida do **SSM**. Traces + métricas, com dashboard e 4 alertas provisionados via `newrelic.tf`.

Detalhes críticos tratados: **force flush** antes do freeze e propagação do **`traceparent` W3C** dentro do `SagaPayload` (trace único `/process → SFN → UpdateStatus`). Logs em JSON com `trace_id`/`span_id` no MDC. Os circuit breakers (Resilience4j) emitem `WARN` na transição de estado quando o provedor de IA cai. Plano completo em [`OBSERVABILITY.md`](./docs/OBSERVABILITY.md).

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

Há dois cenários compostos por **profile groups**: `local-rich` (stack realista na máquina) e `aws` (produção serverless). O grupo `local` é o modo enxuto via LocalStack.

| Eixo | `local-rich` | `aws` (`!local`) |
|---|---|---|
| Fraude | Ollama/Mistral | Bedrock |
| Cache / idempotência | Redis | DynamoDB |
| Ledger / persistência | PostgreSQL | DynamoDB nativo |
| Eventos | Kafka/Redpanda (+ projeção de auditoria) | nenhum (`queue-none`) |
| Saga | Temporal | Step Functions Standard |
| Observability | Grafana `otel-lgtm` | OTLP → New Relic |
| Segredos | env/local | SSM Parameter Store |

> O modo `local` (LocalStack em `:4566`) continua disponível para um setup mais leve, sem a stack completa.

---

## Segurança

- JWT validado por `JwtAuthenticationFilter` + `SecurityConfig` (Spring Security, STATELESS, default-deny).
- Segredos (`jwt_secret`, `nr-license-key`) no **SSM Parameter Store** (nunca em tfvars).
- **Fail-fast em produção:** `AuthSecretsValidationConfig` (profile `env-aws`) aborta o startup se `AUTH_OPAQUE_TOKEN` não estiver definido, evitando subir com default inseguro.
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

**Checklist antes de commitar:** `./mvnw clean test` verde · domínio sem anotações Spring · adapters não vazam para o domínio · sem comentários desnecessários (Clean Code, ver [`CLAUDE.md`](CLAUDE.md)).

---

## Deployment (Terraform)

```bash
cd terraform
terraform init
terraform apply   # cria Lambdas (arm64+SnapStart), Function URLs, DynamoDB,
                  # Step Functions, SSM, e recursos New Relic (dashboard/alertas)
```
Variáveis sensíveis (`jwt_secret`, `nr_license_key`, chaves New Relic) via `terraform.tfvars` (não versionar). Detalhes em [`DEPLOYMENT.md`](./docs/DEPLOYMENT.md).

---

## Roadmap

- [x] **Publicar `getStatusFunction` + Lambda URL** (fluxo assíncrono fechado em produção)
- [x] Pipeline CI/CD (GitHub Actions + OIDC), ver [`CICD.md`](./docs/CICD.md)
- [ ] Smoke test de deploy (1 aprovada + 1 rejeitada, trace único no New Relic, alertas armados)
- [ ] Habilitar Bedrock model access em `us-east-1` e validar fim a fim
- [ ] Rotação de `nr-license-key` sem republish do snapshot (SnapStart)
- [ ] DLQ/redrive e alarmes adicionais
- [ ] Rate limiting por usuário

---

## Testes Locais (massa de dados)

O seed é **automático**: o Postgres executa `scripts/seed-local.sql` na primeira inicialização do volume, e o serviço `redis-seed` seta o score de fraude assim que o Redis sobe.

```bash
docker-compose up -d
# Aguardar todos os serviços healthy, depois iniciar a aplicação:
SPRING_PROFILES_ACTIVE=local-rich ./mvnw spring-boot:run
```

> **Primeiro uso / reset:** se o volume Postgres já existir, o initdb não re-executa.
> Para resetar: `docker-compose down -v && docker-compose up -d`

### Autenticação

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"usuario_teste","password":"senha123"}' | jq -r '.token')
```

---

### Cenário 1: Caminho feliz (APROVADO)

Cartão VISA `4111 1111 1111 1111` · limite R$ 10.000 · sem uso · R$ 500 em 3x · localização: São Paulo Centro (mesmo local do cadastro → sem risco geo)

```bash
CORR=$(curl -s -X POST http://localhost:8080/process \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "txn-happy-001",
    "uuidTransaction": "00000000-0000-0000-0000-000000000001",
    "cardDataRequest": {
      "number": "4111111111111111",
      "cvv": "123",
      "brand": "VISA",
      "name": "JOAO DA SILVA"
    },
    "amount": 500.00,
    "installments": 3,
    "locationCode": "SAO_PAULO_CENTRO",
    "callbackUrl": null
  }' | jq -r '.correlationId')

curl -s "http://localhost:8080/status/$CORR" -H "Authorization: Bearer $TOKEN" | jq .
# Resultado esperado: "status": "APPROVED"
```

---

### Cenário 1b: Caminho feliz com localização aleatória

Omitir `locationCode` faz o sistema sortear um local da tabela-verdade, útil para testes de variação.

```bash
CORR=$(curl -s -X POST http://localhost:8080/process \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "txn-happy-002",
    "uuidTransaction": "00000000-0000-0000-0000-000000000011",
    "cardDataRequest": {
      "number": "4111111111111111",
      "cvv": "123",
      "brand": "VISA",
      "name": "JOAO DA SILVA"
    },
    "amount": 150.00,
    "installments": 1
  }' | jq -r '.correlationId')

curl -s "http://localhost:8080/status/$CORR" -H "Authorization: Bearer $TOKEN" | jq .
```

---

### Cenário 2: Transação negada (crédito insuficiente)

Cartão MASTER `5500 0055 5555 5559` · limite R$ 500 · usado R$ 490 (disponível R$ 10) · R$ 200

```bash
CORR=$(curl -s -X POST http://localhost:8080/process \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "txn-denied-001",
    "uuidTransaction": "00000000-0000-0000-0000-000000000002",
    "cardDataRequest": {
      "number": "5500005555555559",
      "cvv": "321",
      "brand": "MASTER",
      "name": "MARIA SOUZA"
    },
    "amount": 200.00,
    "installments": 1,
    "locationCode": "RIO_DE_JANEIRO",
    "callbackUrl": null
  }' | jq -r '.correlationId')

curl -s "http://localhost:8080/status/$CORR" -H "Authorization: Bearer $TOKEN" | jq .
# Resultado esperado: "status": "REJECTED" (total com juros R$ 203,98 > R$ 10,00 disponível)
```

---

### Cenário 3: Bloqueada pelo anti-fraude (score Redis)

Cartão VISA `4000 0000 0000 0002` · crédito OK · score de fraude 95 em cache Redis (seedado automaticamente)

```bash
CORR=$(curl -s -X POST http://localhost:8080/process \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "txn-fraud-001",
    "uuidTransaction": "00000000-0000-0000-0000-000000000003",
    "cardDataRequest": {
      "number": "4000000000000002",
      "cvv": "999",
      "brand": "VISA",
      "name": "CARLOS FRAUDE"
    },
    "amount": 100.00,
    "installments": 1,
    "locationCode": "SAO_PAULO_CENTRO",
    "callbackUrl": null
  }' | jq -r '.correlationId')

curl -s "http://localhost:8080/status/$CORR" -H "Authorization: Bearer $TOKEN" | jq .
# Resultado esperado: "status": "REJECTED" (fraud score 95 >= threshold 80)
```

---

### Cenário 4: Fraude geográfica (São Paulo → Polo Norte)

Cliente cadastrado em São Paulo tenta transação vinda do Polo Norte, a IA deve atribuir penalidade máxima (+40 pts) por localização fisicamente impossível.

```bash
CORR=$(curl -s -X POST http://localhost:8080/process \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "txn-geo-fraud-001",
    "uuidTransaction": "00000000-0000-0000-0000-000000000004",
    "cardDataRequest": {
      "number": "4111111111111111",
      "cvv": "123",
      "brand": "VISA",
      "name": "JOAO DA SILVA"
    },
    "amount": 300.00,
    "installments": 1,
    "locationCode": "NORTH_POLE",
    "callbackUrl": null
  }' | jq -r '.correlationId')

curl