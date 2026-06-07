package com.empresa.cardtransactionsystem.adapters.inbound.function;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import com.empresa.cardtransactionsystem.adapters.inbound.rest.dto.CardDataRequest;
import com.empresa.cardtransactionsystem.adapters.inbound.rest.dto.CardTransactionRequest;
import com.empresa.cardtransactionsystem.application.orchestrator.TransactionOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Function;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
@DisplayName("ProcessTransactionFunction")
class ProcessTransactionFunctionTest {
    @Mock
    private TransactionOrchestrator orchestrator;
    private Function<CardTransactionRequest, UUID> function;
    @BeforeEach
    void setUp() {
        function = new FunctionsConfig(OpenTelemetry.noop(), new SimpleMeterRegistry()).processTransactionFunction(orchestrator);
    }
    @Test
    @DisplayName("should delegate to orchestrator and return correlationId")
    void shouldDelegateToOrchestratorAndReturnCorrelationId() {
        var uuid = UUID.randomUUID();
        var request = new CardTransactionRequest(
                "TXN-001", uuid,
                new CardDataRequest("4111111111111111", "123", "John Doe", "VISA"),
                new BigDecimal("500.00"), 3
        );
        when(orchestrator.orchestrate(request)).thenReturn(uuid);
        UUID result = function.apply(request);
        assertThat(result).isEqualTo(uuid);
        verify(orchestrator).orchestrate(request);
    }
}
