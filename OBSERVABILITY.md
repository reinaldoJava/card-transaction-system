# Observability & Tracing — Plano de Arquitetura

> Stack alvo: **OpenTelemetry (vendor-neutral) → OTLP → New Relic (free tier)**
> Instrumentação: **Micrometer Observation API + Micrometer Tracing (bridge OTel)**, sem javaagent
> Premissa: custo zero · serverless (Lambda + Step Functions) · Spring Boot 4 / Java 25
> Status: **desenho/plano** (sem implementação)

---

## 1. Por que sair do AspectJ

O `LoggingAspect` (`@Around`) atual:
- Mistura concern de telemetria com a aplicação via proxy AOP (frágil, acopla a Spring AOP).
- Só produz **log**, não dá **trace distribuído** nem **métricas**.
- Não correlaciona uma requisição através de Lambda → Step Functions → Lambda.
- Não tem contexto de span, baggage, nem propagação W3C.

Substituto idiomático no Spring Boot 4: **Micrometer Observation API**. Uma única
`Observation` gera, ao mesmo tempo, span (trace), métrica (timer/contador) e log correlacionado —
sem aspecto manual. O backend é só configuração.

---

## 2. Decisão de stack (trade-off)

| Opção | Cold start | Facilidade | Robustez | Veredito |
|---|---|---|---|---|
| OTel **javaagent** (auto) | Alto (bytecode + snapshot maior) | Alta (zero-code) | Alta | Pesado demais p/ Lambda |
| **ADOT layer** | Médio-alto | Média | Alta (mas orientado a X-Ray) | Overkill p/ New Relic |
| **Micrometer Observation + bridge OTel (OTLP)** | **Baixo-médio** | **Alta** (nativo Spring) | **Alta** (OTel puro) | **Escolhido** |
| OTel SDK manual | Baixo | Baixa (muito código) | Alta | Só se cold start for crítico |

**Escolhido:** Micrometer Observation + Micrometer Tracing bridge OTel + OTLP exporter → New Relic.
Vendor-neutral: trocar de New Relic para X-Ray/Grafana/Honeycomb = mudar config, não código.

> Nota de futuro: o X-Ray **SDK** entra em manutenção em 25/02/2026 e a AWS recomenda OTel.
> Por isso a instrumentação é OTel; o New Relic é só um destino OTLP intercambiável.

---

## 3. Os três pilares e o fluxo

```
                         ┌─────────────────────────────────────────┐
   Lambda (Spring Boot)  │  Micrometer Observation API             │
   /process, saga steps  │   ├─ Tracing  → spans (OTel)            │
                         │   ├─ Metrics  → Micrometer registry     │
                         │   └─ Logs     → logback JSON + MDC       │
                         │        (trace_id / span_id injetados)    │
                         └───────────────┬─────────────────────────┘
                                         │ OTLP/HTTP (protobuf)
                                         ▼
                          New Relic OTLP endpoint  (api-key via SSM)
                          https://otlp.nr-data.net   (US)
                                         │
                                         ▼
                          New Relic: APM · Traces · Logs · Dashboards · Alerts
```

- **Traces:** cada invocação vira um trace; subsegmentos para Bedrock, DynamoDB, Step Functions start.
- **Metrics:** golden signals + métricas de negócio (ver §7) via Micrometer → OTLP.
- **Logs:** logback com encoder JSON; `trace_id`/`span_id` no MDC para correlacionar log ↔ trace no NR.

---

## 4. Sutilezas de serverless (críticas)

### 4.1 Flush antes do freeze
`BatchSpanProcessor` agenda export em background; o Lambda **congela** após retornar e os spans
podem se perder. Opções:
- **Force flush** ao fim do handler (`SdkTracerProvider.forceFlush()` / `OpenTelemetrySdk.close()` no shutdown), ou
- `SimpleSpanProcessor` (exporta síncrono — mais latência por span, ok em baixo volume), ou
- **New Relic Lambda extension layer** (buffer+flush gerenciado, e ainda evita egress de CloudWatch).

Recomendado: **force flush explícito** no wrapper da function + `BatchSpanProcessor` com
`scheduleDelay` curto. Avaliar a NR extension se o egress incomodar.

### 4.2 Propagação de contexto pelo Step Functions
Trace **não** atravessa o Step Functions automaticamente. Para o trace ser ponta-a-ponta
(/process → SFN → validate → fraud → updateStatus), propagar o **W3C `traceparent`**:
- Adicionar campo `traceparent` (e opcional `tracestate`) ao **`SagaPayload`**.
- No `TransactionOrchestrator`, injetar o contexto atual no payload antes de `sagaStarterPort.start()`.
- Em cada saga function, extrair o `traceparent` do payload e abrir o span como filho.

Isso reaproveita o payload que já trafega — sem serviço extra. (Mantém PCI: `traceparent` não é dado sensível.)

### 4.3 SnapStart
O snapshot é tirado após o init. Inicializar o SDK OTel/Micrometer no init faz o custo ser pago
uma vez por versão publicada, não por invocação. Sem javaagent o snapshot fica menor. Bom casamento.

### 4.4 correlationId existente
O `CorrelationIdFilter`/`StructuredLogger` continuam úteis: mapear o `correlationId` de negócio
como **atributo de span** (`transaction.correlation_id`) e/ou **baggage**, complementando o `trace_id`
técnico. Não duplicar: trace_id = correlação técnica, correlationId = chave de negócio.

---

## 5. Onde instrumentar (respeitando hexagonal)

Observability é concern de **infraestrutura** — fica fora do `domain`.

| Camada | Instrumentação |
|---|---|
| `adapters/inbound/function` | Span raiz por function; extrair `traceparent`; tags de entrada |
| `adapters/inbound/rest` (Controllers) | Auto via Observation web do Spring |
| `adapters/outbound/bedrock` | Span "fraud.analyze" + tags: modelId, tokens, latência, nº tool-calls |
| `adapters/outbound/dynamodb` | Span por operação (getItem/putItem/updateItem) |
| `adapters/outbound/saga` | Span "saga.start" + injeção do `traceparent` |
| `domain/*` | **Nada.** Domínio puro permanece sem dependência de telemetria |

Padrão preferido: `@Observed` nos métodos de adapter/use-case, ou `ObservationRegistry` injetado
para spans manuais nos adapters de I/O. Remover `LoggingAspect`; manter `StructuredLogger` só como
encoder JSON.

---

## 6. Dependências e configuração (esboço, sem implementar)

Dependências (Maven):
- `io.micrometer:micrometer-observation`
- `io.micrometer:micrometer-tracing-bridge-otel`
- `io.opentelemetry:opentelemetry-exporter-otlp`
- `net.logstash.logback:logstash-logback-encoder` (logs JSON)
- (opcional) `io.micrometer:micrometer-registry-otlp` para métricas via OTLP

Variáveis de ambiente (Lambda, via Terraform):
```
OTEL_EXPORTER_OTLP_ENDPOINT = https://otlp.nr-data.net
OTEL_EXPORTER_OTLP_HEADERS  = api-key=<NEW_RELIC_LICENSE_KEY>   # vindo do SSM
OTEL_SERVICE_NAME           = card-transaction-system
OTEL_EXPORTER_OTLP_PROTOCOL = http/protobuf
OTEL_METRICS_EXPORTER       = otlp
OTEL_METRIC_EXPORT_INTERVAL = 10000
```
New Relic recomenda **temporalidade DELTA** para métricas OTLP (`OTEL_EXPORTER_OTLP_METRICS_TEMPORALITY_PREFERENCE=delta`).
Confirmar endpoint US vs EU (`otlp.eu01.nr-data.net`) conforme a conta.

Segredo:
- `NEW_RELIC_LICENSE_KEY` no **SSM Parameter Store** (SecureString), lido com a mesma policy
  `lambda_ssm` que já existe para o `jwt_secret`. Nunca em tfvars.

Terraform (a fazer): novo `aws_ssm_parameter` para a license key; env vars acima nas lambdas;
(opcional) `layers = [<new-relic-lambda-extension-arn>]`.

---

## 7. O que medir

**Golden signals (automático via Observation):**
- Latência (p50/p95/p99) por function e por endpoint.
- Erros / taxa de exceção.
- Throughput (req/min).
- Saturação (duração + cold starts; SnapStart hit rate).

**Métricas de negócio (Micrometer custom):**
- `transactions.total{status=APPROVED|REJECTED}` — contador.
- `transaction.saga.duration` — timer ponta-a-ponta.
- `fraud.score` — distribuição (summary).
- `bedrock.latency` e `bedrock.tool_calls` — custo/latência do agente (único item pago).
- `idempotency.cache.hit` — eficácia do cache.

**Alertas sugeridos no New Relic:**
- p95 de `/process` acima de X ms.
- Taxa de REJECTED anômala (possível incidente de fraude/regra).
- Erros de saga / entradas em Compensation.
- Aproximação do teto de 100 GB/mês (guardrail de custo).

---

## 8. Guardrails de custo (free tier 100 GB/mês)

- **Sampling:** em volume de demo, 100% (`parentbased_always_on`). Se crescer, `parentbased_traceidratio`
  (ex: 10–20%) — sampling por trace mantém traces completos.
- Logs em JSON com nível adequado (`INFO` em prod; `DEBUG` só sob demanda) — log é o maior consumidor de GB.
- Métricas com cardinalidade controlada (evitar tags de alta cardinalidade como cardToken em métricas).
- Reavaliar a NR extension vs egress CloudWatch conforme o volume.

---

## 9. Plano de migração (quando for implementar)

1. Adicionar dependências Micrometer/OTel + logback JSON.
2. Configurar `ObservationRegistry` + exporter OTLP (init no startup, compatível com SnapStart).
3. Adicionar `traceparent` ao `SagaPayload` + injeção no orchestrator e extração nas saga functions.
4. Instrumentar adapters de I/O (Bedrock, DynamoDB, SFN) com `@Observed`/spans manuais.
5. Trocar `LoggingAspect` (remover) por logback JSON com MDC de trace; manter `StructuredLogger`.
6. Force flush no wrapper das functions.
7. Terraform: SSM da license key + env vars OTLP nas lambdas.
8. Criar dashboards e alertas no New Relic.
9. Validar trace ponta-a-ponta: um POST /process aparecendo como 1 trace com todos os steps da saga.

---

## 9.1 Local vs AWS + custo

A instrumentação é **idêntica** nos dois ambientes (Micrometer Observation/OTel). Só muda **para
onde o OTLP exporta**, via Spring profile — mesma filosofia do feature flag Ollama/Bedrock.

### Local (`profile local`) — não usar New Relic
Para não gastar a cota de 100 GB e ter feedback instantâneo (e offline, como o Ollama):
- **Console/logging exporter** — spans no stdout, zero infra; ou
- **Jaeger all-in-one no docker** (UI em `localhost:16686`) — traces visuais locais, grátis,
  encaixa no `docker-compose` + localstack já existentes.

(Se um dia quiser ver local no NR: apontar o endpoint com `environment=local`. Não é o padrão.)

### AWS (`profile !local`) — New Relic
OTLP/HTTP → `otlp.nr-data.net`, `api-key` vinda do **SSM Parameter Store** (mesma policy
`lambda_ssm` do `jwt_secret`).

| | Local | AWS |
|---|---|---|
| Exporter | `console` ou `http://localhost:4318` (Jaeger) | `https://otlp.nr-data.net` |
| Credencial | nenhuma | `api-key` no SSM |
| Backend | Jaeger docker / stdout | New Relic |
| Flush | normal | **force flush** antes do freeze |
| traceparent | propaga igual | propaga no `SagaPayload` pelo Step Functions |

### Custo (free tier de 12 meses expirado; always-free intacto)

| Item | Custo |
|---|---|
| New Relic | **$0** — 100 GB/mês de ingest, 1 usuário, *forever* |
| Saída Lambda → New Relic | **$0** — dentro dos 100 GB/mês de DTO grátis (agregado, always-free) |
| CloudWatch | ~$0 — logs mínimos do Lambda, dentro dos 5 GB grátis |
| X-Ray | **$0** — não usado |
| Local (Jaeger/console) | **$0** — roda na máquina |
| **Total incremental** | **$0** em volume de demo/portfólio |

Bedrock continua o **único item pago** (premissa).

**Cuidados para o zero continuar zero:**
- **Não** colocar o Lambda em VPC com **NAT Gateway** (~$32/mês + dados). Hoje não estão em VPC; manter assim, ou usar VPC endpoints.
- Log em `INFO` + **sampling** se o volume crescer — log é o maior consumidor dos 100 GB do NR.
- Tetos que disparam cobrança: NR > 100 GB/mês de ingest, ou DTO AWS agregado > 100 GB/mês. Ambos muito acima do volume atual.

---

## 10. Resumo executivo

Instrumentar **uma vez com OpenTelemetry via Micrometer** (nativo Spring, leve, sem agente),
exportar **OTLP para New Relic free** (APM tipo Datadog, 100 GB/mês forever). Vendor-neutral:
o mesmo código serve para X-Ray, Grafana ou Honeycomb trocando só env vars. Os dois pontos de
atenção serverless — **flush antes do freeze** e **propagação de `traceparent` pelo Step Functions** —
estão endereçados no §4. Domínio permanece puro; telemetria vive só em adapters/config.
