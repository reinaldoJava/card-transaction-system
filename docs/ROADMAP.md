# Roadmap — Foco no pitch de IA + Retorno ao cliente + Kafka local enxuto

> Decisões: **remover OpenSearch** (fora do pitch, custo de cluster), **manter o Kafka só no
> `local-rich`** com um propósito real e barato — **projeção de auditoria numa tabela Postgres**
> (reaproveita o banco, sem cluster). Na **AWS** o barramento é **omitido** (sem MSK grátis) via
> publisher no-op. Adicionar **webhook/callback** (retorno push) e **publicar o endpoint de status
> na AWS** (fecha o polling). Núcleo (`domain`/`application`) intacto, exceto ports.
> O `diagrama.html` já reflete este alvo.

Esforço: **S** pequeno · **M** médio.

---

## A. Excluir

### A1. OpenSearch — **[M]**
- `adapters/outbound/opensearch/OpenSearchAuditAdapter.java`
- `adapters/outbound/opensearch/TransactionAuditDocument.java`
- `adapters/outbound/opensearch/TransactionAuditRepository.java`
- `config/OpenSearchConfig.java`
- Teste: `adapters/outbound/opensearch/OpenSearchAuditAdapterTest.java`
- Dependência: `spring-boot-starter-data-elasticsearch` no `pom.xml` (+ Testcontainers opensearch/elasticsearch, se houver)
- **Nota:** o `TransactionAuditProjector` **não é apagado** — é repontado para Postgres (seção B2).

### A2. SQS event publisher (código morto) — **[S]**
- `adapters/outbound/sqs/SqsDomainEventPublisherAdapter.java`
- `config/SqsConfig.java` (se só servia ao event publisher — conferir)

---

## B. Manter / Ajustar o Kafka (enxuto, só `local-rich`)

### B1. Manter o barramento — **[S]**
- Mantém `adapters/outbound/kafka/KafkaDomainEventPublisherAdapter.java` (`@Profile("queue-kafka")`)
- Mantém `spring-kafka` + Testcontainers `kafka` no `pom.xml`
- Mantém `DomainEventPublisherPort` e as chamadas `publish(...)`:
  - `TransactionOrchestrator` (PENDING + rejeição por cache)
  - `FunctionsConfig.updateStatusFunction`
  - `TransactionSagaActivitiesImpl` (approve/reject)

### B2. Repontar a auditoria para Postgres (em vez de OpenSearch) — **[M]**
- Reescrever `adapters/inbound/kafka/TransactionAuditProjector.java`:
  - `@KafkaListener` (mantém) → grava num **repositório JPA** de auditoria
- Novo: `adapters/outbound/postgres/TransactionAuditEntity.java` + `TransactionAuditJpaRepository.java`
  (tabela `transaction_audit`: correlationId, status, reason, amount, createdAt…)
- Profile do projector e do repositório de auditoria: `@Profile("queue-kafka")` (só `local-rich`)

### B3. Publisher no-op para a AWS — **[S]**
- Novo: `adapters/outbound/event/NoOpDomainEventPublisherAdapter.java` (`@Profile("queue-none")`)
  para o port resolver sem Kafka/MSK na AWS
- (Alternativa futura: SQS na AWS, que é always-free — fora do escopo agora)

---

## C. Adicionar

### C1. Webhook / callback (retorno push) — **[M]**
- **Domínio:** `domain/ports/output/CallbackNotifierPort.java` — `void notify(SagaPayload payload, TransactionResult result)`
- **Adapter:** `adapters/outbound/callback/HttpCallbackNotifierAdapter.java`
  - POST para `payload.callbackUrl()` com o resultado (status, reason, correlationId)
  - Assinatura **HMAC** (header `X-Signature`), segredo do SSM/`@Value`
  - Timeout curto + retry leve (idempotente)
- **Request → payload:** `callbackUrl` (opcional) em `CardTransactionRequest` propagado no `SagaPayload`
  (campo novo, como o `traceparent`)
- **Saga:** Step Functions inclui `callbackUrl` nos `Parameters` do `UpdateStatus` (`step_functions.tf`)
  + `FraudResult`; Temporal já recebe o `SagaPayload` inteiro
- **Pontos de notificação** (espelham onde grava idempotência):
  1. `TransactionOrchestrator` — rejeição imediata por fraud score em cache (decisão final!)
  2. `FunctionsConfig.updateStatusFunction` — approve/reject (Step Functions)
  3. `TransactionSagaActivitiesImpl` — approve/reject (Temporal)

### C2. Publicar o endpoint de status na AWS (fecha o polling) — **[S/M]**
- `getStatusFunction` como Spring Cloud Function (já existe `GetTransactionStatusUseCase`)
- Adicionar à `spring.cloud.function.definition`
- Terraform: `aws_lambda_function` + `aws_lambda_function_url` para status (`lambda.tf`)

---

## D. Ajustar

### D1. Profile groups — **[S]**
```yaml
spring:
  profiles:
    group:
      local-rich: cache-redis,queue-kafka,saga-temporal,ledger-postgres,fraud-ollama,env-local
      aws:        cache-dynamodb,queue-none,saga-stepfunctions,ledger-dynamodb,fraud-bedrock,env-aws
```
(remover `search-opensearch`/`search-none`; `queue-kafka` só no local, `queue-none` na AWS)

### D2. Temporal — registro de worker (verificar duplicidade) — **[S]**
- `TransactionSagaActivitiesImpl` usa `@ActivityImpl` **e** `TemporalConfig` registra manualmente.
  Escolher um: manter `WorkerFactory` manual e remover `@ActivityImpl`/`@WorkflowImpl`, **ou**
  adotar o `temporal-spring-boot-starter` e remover o `TemporalConfig`.

### D3. Threshold de fraude — **[S]**
- `FRAUD_THRESHOLD = 80` hardcoded no `TransactionSagaWorkflowImpl`; alinhar com `fraud.agent.threshold`.

### D4. k8s — **[S]**
- Remover **OpenSearch** de `infrastructure.yaml`/`base-config.yaml`
- **Manter Kafka** (infra local) + Postgres (já presente p/ a tabela de auditoria)

### D5. Documentação — **[S]**
- `LOCAL_DEV.md`: tirar OpenSearch; manter Kafka com auditoria em Postgres
- `README.md`: tirar OpenSearch; manter Kafka (local); adicionar webhook + status endpoint
- `DEEP_DIVE.md`: marcar a lacuna do status endpoint como resolvida (C2)

---

## E. Validar

- `mvn clean test` verde
- Boot `local-rich`: sobe Redis + Postgres + Temporal (um worker) + **Kafka**; publica e o
  `TransactionAuditProjector` grava na tabela `transaction_audit` (Postgres)
- Boot `aws`: sobe com `queue-none` (no-op), sem Kafka/OpenSearch
- Smoke ponta-a-ponta:
  - 1 aprovada + 1 rejeitada
  - Retorno **A (polling)** `GET /status/{id}` e **B (webhook)** POST assinado (HMAC)
  - Registro de auditoria criado no Postgres (local-rich)
  - Trace único no New Relic/Jaeger

---

## Antes / Depois

| Tema | Antes | Depois |
|---|---|---|
| Kafka | local + aws, alimentava OpenSearch | **só `local-rich`**, alimenta **auditoria em Postgres** |
| OpenSearch | cluster dedicado | **removido** |
| Auditoria | OpenSearch | **tabela Postgres** (reaproveita o banco) |
| Eventos na AWS | Kafka/MSK | **no-op** (sem MSK grátis) |
| Retorno ao cliente | só polling (nem publicado na AWS) | **polling (publicado) + webhook** |
| Foco do diagrama | infra dispersa | **decisão por IA + retorno + Kafka local enxuto** |

Núcleo (`domain`/`application`) intacto; ports tocados: **mantém** `DomainEventPublisherPort`,
**adiciona** `CallbackNotifierPort`.
