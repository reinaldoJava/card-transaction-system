# Observability

Instrumentação **vendor-neutral** com **Micrometer Observation + OpenTelemetry (bridge OTel)** — sem
javaagent. A mesma instrumentação serve aos três pilares (traces, métricas, logs); o **backend é só
configuração por profile**.

## Backends por ambiente

| Pilar | `local-rich` | `aws` |
|---|---|---|
| Traces | OTLP → **grafana/otel-lgtm** (Tempo) | OTLP → **New Relic** |
| Métricas | OTLP → otel-lgtm (Prometheus) | OTLP → New Relic (`FlushableOtlpMeterRegistry`) |
| Logs | OTLP → otel-lgtm (Loki), via `OpenTelemetryAppender` no logback | logback JSON → **CloudWatch** |
| UI | **Grafana** em `http://localhost:3000` | New Relic (APM) / Jaeger opcional |

A imagem **`grafana/otel-lgtm`** (no `docker-compose`) é um "tudo-em-um": OTel Collector + Tempo +
Prometheus + Loki + Grafana, com datasources já provisionados. Recebe OTLP em `4317`/`4318`.

Config relevante:
- `application-local-rich.yml`: `management.otlp.tracing.endpoint`, `management.otlp.metrics.export.url`
  e `management.otlp.logging.endpoint` → `http://localhost:4318/...`.
- `LocalObservabilityConfig` (env-local): exporter de spans (lgtm; console como fallback).
- `ProdObservabilityConfig` (env-aws): exporters de span/métricas para o New Relic, `api-key` lida do
  **SSM** (`/card-transaction-system/nr-license-key`).
- `logback-spring.xml`: `OpenTelemetryAppender` ativo só no profile `local-rich` (logs → Loki);
  em prod os logs ficam no CloudWatch.

## Sutilezas de serverless (tratadas)

- **Force flush antes do freeze:** o wrapper das Spring Cloud Functions faz `forceFlush` de traces
  **e** métricas (`FlushableOtlpMeterRegistry`) antes do Lambda congelar, para não perder telemetria
  no ciclo de vida curto.
- **Trace único ponta-a-ponta:** o `traceparent` (W3C) é propagado dentro do `SagaPayload`, ligando
  `/process → saga (Temporal/Step Functions) → UpdateStatus` num só trace.
- **Correlação log↔trace:** o Micrometer injeta `traceId`/`spanId` no MDC; o logback os emite como
  `trace_id`/`span_id`. O `correlationId` de negócio vem do `CorrelationIdFilter` (MDC por requisição).

## Métricas de negócio

- `transactions.*` (submitted / completed{outcome,reason}) e `fraud.score.distribution`.
- `webhook.delivery{result=delivered|failed}` — entrega do callback (após retry).
- Transições do circuit breaker dos adapters de IA logam `WARN` (`OPEN`/`HALF_OPEN`/`CLOSED`).

## Dashboards & alertas (New Relic / Terraform)

`terraform/newrelic.tf` provisiona um dashboard + 4 alertas NRQL (taxa de rejeição de fraude,
latência do Bedrock, erros de saga, baixo throughput).

## Ver no local

```bash
docker-compose up -d
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local-rich"
# Grafana: http://localhost:3000  (Explore → Tempo=traces · Prometheus=métricas · Loki=logs)
```
