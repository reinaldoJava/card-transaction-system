package com.empresa.cardtransactionsystem.config;

import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("local")
public class LocalObservabilityConfig {

    @Bean
    public SpanExporter localSpanExporter(
            @Value("${management.otlp.tracing.endpoint:}") String endpoint) {
        if (endpoint.isBlank()) {
            return LoggingSpanExporter.create();
        }
        return OtlpHttpSpanExporter.builder()
                .setEndpoint(endpoint)
                .build();
    }
}
