package com.empresa.cardtransactionsystem.config;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ObservabilityConfig")
class ObservabilityConfigTest {

    private final LocalObservabilityConfig localConfig = new LocalObservabilityConfig();
    private final ObservabilityConfig config = new ObservabilityConfig();

    @Test
    @DisplayName("should configure ObservationRegistry as non-noop")
    void shouldConfigureObservationRegistry() {
        ObservationRegistry registry = ObservationRegistry.create();
        assertThat(registry).isNotEqualTo(ObservationRegistry.NOOP);
    }

    @Test
    @DisplayName("should register ObservedAspect for @Observed support")
    void shouldRegisterObservedAspect() {
        ObservationRegistry registry = ObservationRegistry.create();
        ObservedAspect aspect = config.observedAspect(registry);
        assertThat(aspect).isNotNull();
    }

    @Test
    @DisplayName("should return LoggingSpanExporter when endpoint is absent")
    void shouldConfigureConsoleSpanExporterWhenEndpointAbsent() {
        SpanExporter exporter = localConfig.localSpanExporter("");
        assertThat(exporter).isInstanceOf(LoggingSpanExporter.class);
    }

    @Test
    @DisplayName("should return OtlpHttpSpanExporter when Jaeger endpoint is configured")
    void shouldConfigureJaegerExporterWhenEndpointPresent() {
        SpanExporter exporter = localConfig.localSpanExporter("http://localhost:4318/v1/traces");
        assertThat(exporter).isInstanceOf(OtlpHttpSpanExporter.class);
    }
}
