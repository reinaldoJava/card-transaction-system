package com.empresa.cardtransactionsystem.adapters.inbound.logging;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class StructuredLoggerTest {

    private Logger slf4jLogger;
    private Tracer tracer;
    private StructuredLogger structuredLogger;

    @BeforeEach
    void setUp() {
        slf4jLogger = mock(Logger.class);
        tracer = mock(Tracer.class);
        structuredLogger = StructuredLogger.of(slf4jLogger, tracer, "test-correlation-123");
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void shouldInjectTraceAndSpanIdsFromTracerIntoMdc() {
        String expectedTraceId = "abcdef123456";
        String expectedSpanId = "7890ghijk";

        Span mockSpan = mock(Span.class);
        TraceContext mockContext = mock(TraceContext.class);

        when(tracer.currentSpan()).thenReturn(mockSpan);
        when(mockSpan.context()).thenReturn(mockContext);
        when(mockContext.traceId()).thenReturn(expectedTraceId);
        when(mockContext.spanId()).thenReturn(expectedSpanId);

        structuredLogger.info("Iniciando processamento de transação");

        assertThat(MDC.get("trace_id")).isEqualTo(expectedTraceId);
        assertThat(MDC.get("span_id")).isEqualTo(expectedSpanId);
        assertThat(MDC.get("correlationId")).isEqualTo("test-correlation-123");
        verify(slf4jLogger).info("Iniciando processamento de transação");
    }

    @Test
    void shouldHandleLoggingWithoutActiveSpan() {
        when(tracer.currentSpan()).thenReturn(null);

        String[] capturedExtraInfo = {null};
        doAnswer(inv -> { capturedExtraInfo[0] = MDC.get("extra_info"); return null; })
                .when(slf4jLogger).info(anyString());

        structuredLogger.info("Log sem span ativo", "extra_info", "valor");

        assertThat(MDC.get("trace_id")).isNull();
        assertThat(capturedExtraInfo[0]).isEqualTo("valor");
        assertThat(MDC.get("extra_info")).isNull();
    }

    @Test
    void shouldClearContextCorrectly() {
        structuredLogger.addContext("custom_key", "custom_value");
        structuredLogger.info("Log com contexto");
        assertThat(MDC.get("custom_key")).isEqualTo("custom_value");

        structuredLogger.clear();

        assertThat(MDC.get("custom_key")).isNull();
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void shouldRemoveTransientKeyValuePairsAfterLog() {
        when(tracer.currentSpan()).thenReturn(null);

        structuredLogger.info("msg", "transient_key", "transient_val");

        assertThat(MDC.get("transient_key")).isNull();
        assertThat(MDC.get("correlationId")).isEqualTo("test-correlation-123");
    }
}
