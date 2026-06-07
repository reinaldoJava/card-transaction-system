package com.empresa.cardtransactionsystem.adapters.outbound.observability;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TraceparentExtractor")
class TraceparentExtractorTest {

    @RegisterExtension
    static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

    private final TraceparentExtractor extractor = new TraceparentExtractor();

    @Test
    @DisplayName("extract() returns null when no active span")
    void extractReturnsNullWithNoActiveSpan() {
        String traceparent = extractor.extract();
        assertThat(traceparent).isNull();
    }

    @Test
    @DisplayName("extract() returns W3C traceparent when span is active")
    void extractReturnsTraceparentWithActiveSpan() {
        Tracer tracer = otelTesting.getOpenTelemetry().getTracer("test");
        Span span = tracer.spanBuilder("test-span").startSpan();

        try (Scope ignored = span.makeCurrent()) {
            String traceparent = extractor.extract();
            assertThat(traceparent).isNotNull();
            assertThat(traceparent).startsWith("00-");
            assertThat(traceparent.split("-")).hasSize(4);
        } finally {
            span.end();
        }
    }

    @Test
    @DisplayName("restore() with valid traceparent makes it the current context")
    void restoreSetParentContext() {
        String traceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

        try (Scope scope = extractor.restore(traceparent)) {
            String extracted = extractor.extract();
            assertThat(extracted).isNotNull();
            assertThat(extracted).contains("4bf92f3577b34da6a3ce929d0e0e4736");
        }
    }

    @Test
    @DisplayName("restore() with null traceparent does not throw")
    void restoreWithNullDoesNotThrow() {
        try (Scope scope = extractor.restore(null)) {
            assertThat(scope).isNotNull();
        }
    }
}
