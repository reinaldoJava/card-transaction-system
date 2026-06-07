package com.empresa.cardtransactionsystem.adapters.inbound.logging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("StructuredLogger")
class StructuredLoggerTest {

    private Logger mockLogger;
    private StructuredLogger structuredLogger;

    @BeforeEach
    void setUp() {
        MDC.clear();
        mockLogger = mock(Logger.class);
        structuredLogger = StructuredLogger.of(mockLogger);
    }

    @Test
    @DisplayName("should create with generated correlationId when not provided")
    void shouldCreateWithGeneratedCorrelationId() {
        assertThat(structuredLogger.getCorrelationId()).isNotNull().isNotBlank();
    }

    @Test
    @DisplayName("should create with provided correlationId")
    void shouldCreateWithProvidedCorrelationId() {
        String providedId = "txn-123";
        StructuredLogger logger = StructuredLogger.of(mockLogger, providedId);

        assertThat(logger.getCorrelationId()).isEqualTo(providedId);
    }

    @Test
    @DisplayName("should add correlationId to MDC on creation")
    void shouldAddCorrelationIdToMdc() {
        String correlationId = structuredLogger.getCorrelationId();

        assertThat(MDC.get("correlationId")).isEqualTo(correlationId);
    }

    @Test
    @DisplayName("should add custom context to MDC")
    void shouldAddCustomContextToMdc() {
        structuredLogger.addContext("userId", "user-123");
        structuredLogger.addContext("transactionId", "txn-456");

        assertThat(MDC.get("userId")).isEqualTo("user-123");
        assertThat(MDC.get("transactionId")).isEqualTo("txn-456");
    }

    @Test
    @DisplayName("should remove context from MDC")
    void shouldRemoveContextFromMdc() {
        structuredLogger.addContext("key", "value");
        assertThat(MDC.get("key")).isEqualTo("value");

        structuredLogger.removeContext("key");
        assertThat(MDC.get("key")).isNull();
    }

    @Test
    @DisplayName("should clear all MDC context")
    void shouldClearAllMdcContext() {
        structuredLogger.addContext("key1", "value1");
        structuredLogger.addContext("key2", "value2");

        structuredLogger.clear();

        assertThat(MDC.get("key1")).isNull();
        assertThat(MDC.get("key2")).isNull();
    }
}
