# Arquitetura — Local (`local-rich`) vs AWS (`aws`)

Documento técnico explicando os dois ambientes do **Card Transaction System** e a justificativa de
cada escolha. O diferencial do projeto é a **validação de transação por um agente de IA**
(Bedrock na nuvem, Ollama no local); todas as demais decisões giram em torno de dois objetivos
distintos por ambiente: **realismo de produção no local** e **custo zero na AWS** (conta com poucos
serviços gratuitos).

---

## 1. Princípio que torna isso possível: Hexagonal + 2 profiles

A aplicação segue **Arquitetura Hexagonal (Ports & Adapters) + DDD**. Toda dependência externa
(banco, cache, mensageria, orquestrador de saga, LLM, segredos, observabilidade) está atrás de uma
*porta* no domínio. Cada tecnologia é um **adapter** que implementa essa porta, selecionado por
**Spring Profile**.

Isso permite que **o mesmo código** rode em duas montagens completamente diferentes, trocando
apenas configuração — `domain/` e `application/` não mudam uma linha. A seleção é feita por
**profiles de capacidade** compostos em **profile groups**:

```yaml
spring:
  profiles:
    group:
      local-rich: cache-redis,  queue-kafka, saga-temporal,      ledger-postgres, fraud-ollama,  env-local
      aws:        cache-dynamodb,queue-none,  saga-stepfunctions, ledger-dynamodb, fraud-bedrock, env-aws
```

Ativa-se um único grupo (`-Dspring.profiles.active=local-rich` ou `aws`). Os eixos são ortogonais:
trocar o cache não afeta a saga, o ledger, etc. Essa é a base que permite ter, ao mesmo tempo, um
**ambiente local robusto** e um **ambiente AWS minimalista de custo zero**.

---

## 2. Fluxo de negócio (idêntico nos dois ambientes)

A autorização é **assíncrona**, e essa é a decisão arquitetural central:

1. `POST /process` (com JWT) → o `TransactionOrchestrator` checa **idempotência** e o **fraud score
   em cache**, persiste a transação como `PENDING`, injeta o `traceparent` (W3C) e dispara a saga.
   Responde imediatamente **`202 Accepted` + `correlationId`**.
2. A **saga** (em background) executa: `ValidateBusinessRules` → **`AnalyzeFraud` (IA)** →
   `UpdateStatus` (decide `APPROVED`/`REJECTED`, grava idempotência). Erro em qualquer passo →
   `RollbackCompensation`.
3. **Retorno ao cliente**, em dois modos: **A) Polling** `GET /status/{correlationId}` (lê o ledger)
   e **B) Webhook** — ao decidir, o `UpdateStatus` faz um `POST` assinado (HMAC) para a `callbackUrl`
   informada no request.

**Por que assíncrono?** Numa autorização síncrona, o processo de entrada ficaria **bloqueado**
esperando a saga (validação + chamada de IA) terminar. Na AWS isso significaria **pagar tempo de
Lambda ocioso** — justamente o que se quer evitar sob custo zero. O modelo `202` + retorno desacopla
a entrada da decisão: a Lambda de entrada retorna em milissegundos e a saga corre em background.

---

## 3. Ambiente AWS (`aws`) — produção sob restrição de custo

Premissa: a conta tem o **free tier de 12 meses expirado**, mas os serviços **always-free**
continuam. Cada escolha maximiza o uso desses serviços sempre-gratuitos. **Bedrock é o único item
pago** — e ele é o coração do produto.

| Preocupação | Escolha na AWS | Justificativa |
|---|---|---|
| Entrada HTTP | **Lambda Function URL** (sem API Gateway) | A Function URL já é o endpoint HTTPS; API Gateway/WAF teriam custo. Lambda é always-free (1M req + 400k GB-s/mês). |
| Runtime | **Lambda arm64 + SnapStart** | arm64 (Graviton) ~20% mais barato; SnapStart corta o cold start do Spring Boot ~10×. Ambos grátis. |
| Orquestração da saga | **Step Functions STANDARD** | Standard tem **4.000 transições/mês grátis**; Express **não** tem free tier. Para o volume de demo, Standard fica em $0. |
| Ledger / users / profiles | **DynamoDB (instância única, on-demand)** | Always-free generoso (25 GB + milhões de req/mês); zero administração; sem custo de Aurora/RDS. |
| Histórico (para a IA) | **DynamoDB (`DynamoDbTransactionHistoryAdapter`)** | Mantém o agente de fraude alimentado em produção, sem banco extra. |
| Cache / idempotência | **Tabela DynamoDB com TTL** | Evita ElastiCache (que não é always-free). Reaproveita o DynamoDB já existente. |
| Mensageria / auditoria | **Nenhuma (`queue-none`, publisher no-op)** | MSK/Kafka não é gratuito. A auditoria event-driven não agrega ao produto na conta restrita, então o port de eventos é um no-op. |
| Análise de fraude (IA) | **Amazon Bedrock** (agente ReAct) | É o diferencial do projeto e o **único custo aceito**. |
| Segredos | **SSM Parameter Store (SecureString)** | Grátis (Secrets Manager cobra por segredo). Guarda `jwt_secret` e a license key do New Relic. |
| Observabilidade | **OTel → New Relic (free) + CloudWatch** | New Relic free: 100 GB/mês, sem custo. O egress Lambda→New Relic cabe nos 100 GB/mês de DTO grátis. |
| Retorno ao cliente | **Polling (`GET /status`) + Webhook** | Ambos ~zero custo; sem WebSocket/API Gateway (que cobrariam por conexão/mensagem). |

**Resumo da lógica AWS:** tudo que pode ser substituído por um serviço always-free foi substituído;
o que não tem versão gratuita (Aurora, ElastiCache, MSK, Secrets Manager, API Gateway) foi **trocado
por equivalente gratuito ou omitido**. O único gasto deliberado é o Bedrock, porque é o produto.

---

## 4. Ambiente local (`local-rich`) — fidelidade a um sistema real

No local **não há restrição de custo**, então o objetivo se inverte: montar um stack **próximo de um
sistema de cartões de verdade**, para desenvolver e demonstrar com componentes de mercado — tudo
rodando em Docker/k8s, **sem tocar a nuvem** e **sem custo** (inclusive a IA, via Ollama).

| Preocupação | Escolha local | Justificativa |
|---|---|---|
| Cache / idempotência / velocity | **Redis** | Padrão de mercado em pagamentos: TTL nativo, operações atômicas (`SETNX`, `INCR`), latência sub-ms. Modela idempotência e *velocity* de forma realista. |
| Ledger / users / profiles | **PostgreSQL (JPA)** | Movimentação de dinheiro pede **ACID** e consistência forte. Um relacional é mais fiel ao núcleo transacional do que um NoSQL. |
| Orquestração da saga | **Temporal** | Orquestrador *durable* muito usado em fintech: retries, compensação, timeouts e visibilidade de execução. Saga robusta de verdade. |
| Mensageria | **Kafka** | Barramento de eventos de transação — padrão do setor para audit/CDC/integração. |
| Auditoria | **Projeção Kafka → tabela Postgres** | Dá ao Kafka um consumidor **real e barato** (event-driven audit) **sem** cluster de busca (OpenSearch). Reaproveita o Postgres já presente. |
| Análise de fraude (IA) | **Ollama / Mistral** | Roda o agente de IA **localmente, offline e grátis** — o diferencial do projeto funciona sem depender da nuvem. |
| Segredos | **`@Value` / config local** | Sem SSM; segredos por variável/arquivo no dev. |
| Observabilidade | **Jaeger / Grafana (OTel Collector)** | Mesma instrumentação OTel, exportando para um backend local — sem consumir a cota do New Relic. |
| Infra | **Docker Compose / k8s (kind/k3d)** | Sobe Redis, Postgres, Kafka, Temporal e a stack de observabilidade como um ambiente coeso. |

**Resumo da lógica local:** cada componente é o que um sistema de cartões usaria em produção real
(Redis, Postgres, Temporal, Kafka), exercitando o código nos mesmos pontos de integração — mas sem
custo e sem nuvem. A IA roda via Ollama, mantendo o pitch demonstrável offline.

---

## 5. Comparativo lado a lado

| Eixo | `local-rich` (realista) | `aws` (custo zero) |
|---|---|---|
| Entrada | Serviço web (controllers REST) | Lambda Function URL |
| Cache | Redis | DynamoDB (TTL) |
| Ledger | PostgreSQL (ACID) | DynamoDB (instância única) |
| Histórico p/ IA | Postgres | DynamoDB |
| Saga | Temporal | Step Functions STANDARD |
| Mensageria/auditoria | Kafka → auditoria Postgres | nenhuma (no-op) |
| IA (fraude) | Ollama / Mistral | Amazon Bedrock |
| Segredos | `@Value`/local | SSM Parameter Store |
| Observabilidade | Jaeger / Grafana | New Relic (free) |
| Orquestração de infra | Docker / k8s | Lambdas serverless |
| Objetivo | máxima fidelidade | custo zero |

Note que as duas colunas são **o mesmo binário**: o que muda é o grupo de profile ativo e os adapters
selecionados.

---

## 6. Decisões transversais (valem nos dois ambientes)

- **Idempotência:** chave de negócio (`transactionId`) guardada no cache/ledger; reprocessamentos
  retornam o resultado já decidido, evitando dupla cobrança.
- **PCI:** após a tokenização, só trafega o `cardToken` (nunca PAN/CVV) pela saga; no Step Functions,
  `include_execution_data=false`.
- **Observabilidade vendor-neutral:** instrumentação única via **Micrometer Observation + OpenTelemetry**
  (sem javaagent), com `traceparent` propagado pela saga para um **trace único** ponta-a-ponta. Trocar
  o backend (New Relic ↔ Jaeger/Grafana) é só configuração.
- **Retorno desacoplado:** polling (`GET /status`) como baseline universal e webhook (HMAC) como push
  para clientes servidor-a-servidor.
- **IA como diferencial:** a decisão de fraude por LLM é o núcleo do produto; o restante da arquitetura
  existe para servir essa decisão de forma barata (AWS) ou realista (local).

---

## 7. Em uma frase

> O mesmo código hexagonal roda como um **stack de pagamentos realista** no local (Redis, PostgreSQL,
> Temporal, Kafka, Ollama) e como um **serverless de custo zero** na AWS (Lambda, DynamoDB, Step
> Functions, Bedrock), porque a decisão de infraestrutura nunca esteve no domínio — está num profile.
