package com.empresa.cardtransactionsystem.config;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.api.GlobalOpenTelemetry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TracingConfig {
    @Bean
    public Tracer micrometerTracer() {
        // Recupera o tracer nativo do ecossistema OpenTelemetry carregado no classpath
        io.opentelemetry.api.trace.Tracer otelTracer = GlobalOpenTelemetry.getTracer("card-transaction-system");

        // Acopla o gerenciador de escopo do Micrometer
        OtelCurrentTraceContext currentTraceContext = new OtelCurrentTraceContext();

        return new OtelTracer(
                otelTracer,
                currentTraceContext,
                event -> {}
        );
    }
    @Bean
    public io.opentelemetry.api.OpenTelemetry otelOpenTelemetry() {
        return GlobalOpenTelemetry.get();
    }
}
