# Local Dev — Estratégia de ambiente robusto (próximo de produção real)

> Objetivo: rodar localmente uma stack **mais realista de um sistema de cartões** (sem as
> simplificações de custo-zero da AWS), com **mínima alteração de código** — graças à
> arquitetura hexagonal. Núcleo (`domain/`, `application/`) permanece intocado; troca-se infra
> escrevendo **adapters novos** atrás dos *ports* existentes + seleção por *profile*.

---

## 1. Princípio: por que dá pra trocar tudo sem mexer no core

Toda dependência externa está atrás de um *port* (interface no domínio). Trocar a implementação =
escrever um adapter novo em `adapters/outbound/...` + ligar via `@Profile` (profile groups)
— exatamente o padrão já usado no `FraudAnalysisAdapterFactory` (Bedrock ↔ Ollama).

```
domain/ports/output/CachePort  ◄── DynamoDbCacheAdapter        (AWS / atual)
                               ◄── RedisCacheAdapter           (local-rich)   ← novo, sem tocar no core
```

**Regra de ouro:** nenhuma mudança em `domain/` ou `application/`. Só adapters + config + infra.

---

## 2. Mapa: hoje (AWS simplificado) → local realista

| Responsabilidade | Port | AWS (free) | Local realista | Esforço |
|---|---|---|---|---|
| Cache / idempotência / velocity | `CachePort` | DynamoDB | **Redis** (TTL, INCR atômico) | baixo — 1 adapter |
| Ledger, users, profiles | `TransactionRepositoryPort`, `UserRepositoryPort`, `ClientProfilePort` | DynamoDB single-table | **PostgreSQL** (ACID) ou **DynamoDB Local** real | médio (Postgres) / baixo (DDB Local) |
| Histórico de transações | `TransactionHistoryPort` | DynamoDB | PostgreSQL / Kafka + projeção | médio |
| Mensageria / eventos | `TransactionQueuePort` | SQS | **Kafka / Redpanda** | baixo — 1 adapter |
| Orquestração da saga | `SagaStarterPort` | Step Functions | **Temporal** (durable workflow) | médio — adapter + workers |
| Fraude | `FraudAnalysisPort` | Bedrock | **Ollama** (já existe) | zero |
| Busca / auditoria | `AuditSearchPort` | NoOp | **PostgreSQL** (`PostgresAuditAdapter`) | implementado |
| Observability | OTel (Micrometer) | New Relic | **OTel Collector + Grafana (Tempo/Prometheus/Loki)** ou Jaeger | baixo — config |

A coluna "esforço" é sempre **adapter + config**. O core não muda.

---

## 3. Por que cada escolha (realismo de um sistema de cartões)

- **Redis (cache):** idempotência de autorização e contadores de *velocity* (nº de transações em
  janela de tempo) são clássicos de Redis em pagamentos — TTL nativo, operações atômicas (`INCR`,
  `SETNX`), latência sub-ms. Substitui o uso de DynamoDB como cache.
- **PostgreSQL (ledger):** movimentação de dinheiro pede **ACID** e consistência forte. DynamoDB
  foi a simplificação de custo; localmente um relacional é mais fiel ao núcleo transacional.
  Alternativa de menor esforço: **DynamoDB Local** (imagem `amazon/dynamodb-local`) — DynamoDB real,
  não LocalStack, mantendo o adapter atual e mudando só o endpoint.
- **Kafka / Redpanda (eventos):** o barramento de eventos de transação (audit, CDC, projeções,
  integração com antifraude/contabilidade) é padrão de mercado. Redpanda é Kafka-compatível e
  bem mais leve para rodar local.
- **Temporal (saga):** Step Functions é AWS-only. Temporal é o orquestrador *durable* mais usado
  em fintech para sagas de pagamento (retry, compensação, timeouts, visibilidade). Fica atrás do
  `SagaStarterPort`.
- **PostgreSQL (audit):** auditoria e busca de transações via `PostgresAuditAdapter` (profile
  `ledger-postgres`) — mesma base ACID do ledger, sem dependência adicional. No perfil `aws`
  (`ledger-dynamodb`) usa `NoOpAuditAdapter`.
- **Grafana stack (observability):** localmente, em vez de mandar OTLP para o New Relic, aponta-se
  o exporter para um **OTel Collector** → Tempo (traces) + Prometheus (métricas) + Loki (logs),
  tudo visualizado no Grafana. Mesmo código instrumentado, só muda o destino (já previsto no
  `LocalObservabilityConfig`).

---

## 4. Como ligar sem mexer no core (`@Profile` + profile groups)

O código já é **padronizado em `@Profile`** (local vs aws). Mantemos essa convenção — nada de
`@ConditionalOnProperty`. Tanto `@Profile` quanto `@ConditionalOnProperty` são resolvidos **no
startup** (trocar exige restart), então `@Profile` cobre o caso perfeitamente; um "feature flag de
runtime" (Unleash/LaunchDarkly) seria overkill para escolher infraestrutura.

O risco de explosão combinatória (Redis+Kafka+Temporal vs DynamoDB+SQS+StepFunctions...) se resolve
com **profiles finos por capacidade** + **profile groups** que os compõem.

### 4.1 Cada adapter marca seu profile fino

```java
@Component
@Profile("cache-redis")
public class RedisCacheAdapter implements CachePort { ... }

@Component
@Profile("cache-dynamodb")
public class DynamoDbCacheAdapter implements CachePort { ... }
```

Mesma ideia para os demais eixos: `queue-kafka`/`queue-sqs`, `saga-temporal`/`saga-stepfunctions`,
`ledger-postgres`/`ledger-dynamodb`, `fraud-ollama`/`fraud-bedrock`.

### 4.2 Profile groups compõem os finos (e resolvem o "default")

Para evitar complexidade, o sistema é consolidado em apenas **dois cenários** principais. O payload de entrada das Sagas (`SagaPayload`) é **idêntico** para ambos, sendo a tradução de infraestrutura responsabilidade exclusiva de cada adapter.

```yaml
# application.yml
spring:
  profiles:
    group:
      local-rich: cache-redis,queue-kafka,saga-temporal,ledger-postgres,fraud-ollama,env-local
      aws:        cache-dynamodb,queue-none,saga-stepfunctions,ledger-dynamodb,fraud-bedrock,env-aws
```

Ativa-se só o grupo, que expande para os membros:

```
spring.profiles.active=local-rich
```

Conexões de cada tecnologia ficam num `application-local-rich.yml`:

```yaml
spring:
  data:
    redis: { host: localhost, port: 6379 }
  datasource:
    url: jdbc:postgresql://localhost:5432/cards
  kafka:
    bootstrap-servers: localhost:9092
temporal:
  target: localhost:7233
management:
  otlp:
    tracing:
      endpoint: http://localhost:4318/v1/traces   # OTel Collector local
```

Vantagens: eixos **ortogonais** (troca um sem mexer nos outros), **zero explosão combinatória**,
defaults claros via grupos e **100% dentro do padrão `@Profile`** já adotado.

### 4.3 Unificar a fraude no mesmo padrão

Hoje o `FraudAnalysisAdapterFactory` é a exceção — seleciona por propriedade
(`fraud-analysis.provider`) + factory, ou seja, um segundo padrão. Para padronizar, migrar para
`@Profile("fraud-ollama")` / `@Profile("fraud-bedrock")` (como nos grupos acima) e **aposentar o
factory**. Assim fica um único mecanismo de seleção em todo o projeto.

---

## 5. Kubernetes local

**Ponto a favor:** o app já expõe os controllers REST (`AuthController`, `ProcessController`,
`StatusController`), então roda como **serviço web sempre-ligado** — mais próximo de um sistema de
cartões real do que Lambda com cold start. Não há mudança de código para virar serviço.

Passos:

1. **Cluster local:** `kind` ou `k3d` (k3s em Docker, leve).
2. **App:** usar o `Dockerfile` existente → `Deployment` + `Service` + `Ingress` (ou um Helm chart).
3. **Infra como pods/StatefulSets:** Redis, PostgreSQL, Redpanda/Kafka, Temporal e a stack
   Grafana — via charts (Bitnami) ou manifests. Recomendado: validar primeiro no
   `docker-compose` e só então migrar para k8s.
4. **Saga:** workers Temporal como `Deployment` próprio no mesmo cluster.
5. **Config:** `SPRING_PROFILES_ACTIVE=local-rich` via `ConfigMap`; segredos via `Secret`.

Sugestão de evolução: `docker-compose` (rápido para dev diário) → `kind`/`k3d` (ensaiar
deployment, probes, HPA, service discovery) → opcional Helm chart parametrizado.

---

## 6. docker-compose alvo (esboço de serviços)

```yaml
services:
  redis:        # cache / idempotência / velocity
    image: redis:7
    ports: ["6379:6379"]

  postgres:     # ledger ACID
    image: postgres:16
    environment: { POSTGRES_DB: cards, POSTGRES_PASSWORD: dev }
    ports: ["5432:5432"]

  redpanda:     # Kafka-compatível (eventos)
    image: redpandadata/redpanda:latest
    ports: ["9092:9092"]

  temporal:     # orquestração de saga
    image: temporalio/auto-setup:latest
    ports: ["7233:7233"]

  ollama:       # fraude local (já usado)
    image: ollama/ollama
    ports: ["11434:11434"]

  otel-collector + grafana + tempo + prometheus + loki   # observability local
```

---

## 7. Faseamento (mais realismo por menos esforço)

1. **Redis no `CachePort`** — maior ganho, menor esforço (idempotência + velocity reais).
2. **Kafka/Redpanda no `TransactionQueuePort`** — eventos de transação (audit/CDC/projeções).
3. **PostgreSQL** no ledger (ACID) — ou DynamoDB Local para fidelidade ao prod.
4. **Temporal** substituindo Step Functions na saga.
5. **k8s (kind/k3d)** orquestrando app + infra + stack Grafana.

---

## 8. Princípios para manter o custo de mudança baixo

- Um adapter por port; nunca lógica de negócio no adapter.
- Seleção por `@Profile` fino + profile groups (declarativo), não por `if` em código.
- `domain/` e `application/` permanecem livres de qualquer SDK/cliente de infra.
- Cada nova tecnologia entra com: (1) adapter, (2) bloco no `application-local-rich.yml`,
  (3) serviço no `docker-compose`/manifesto k8s. Nada além disso.
- Testes de integração por adapter (Testcontainers já está no projeto): `RedisCacheAdapterTest`,
  `KafkaQueueAdapterTest`, etc., sem tocar nos testes de domínio.

---

## 9. Resumo

A hexagonal transforma "rodar uma stack realista localmente" em **escrever adapters e ligar
profiles** — sem refatorar o núcleo. Comece por Redis (cache) e Kafka (eventos), que dão o maior
salto de realismo com o menor esforço, depois evolua para Postgres, Temporal e k8s conforme
a necessidade. O mesmo código que roda como Lambda na AWS roda como serviço web no
Kubernetes local — porque a decisão de infra nunca esteve no domínio.
