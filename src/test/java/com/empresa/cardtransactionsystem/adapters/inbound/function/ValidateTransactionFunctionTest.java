package com.empresa.cardtransactionsystem.adapters.inbound.function;

import com.empresa.cardtransactionsystem.adapters.outbound.observability.TraceparentExtractor;
import com.empresa.cardtransactionsystem.domain.model.Brand;
import com.empresa.cardtransactionsystem.domain.model.CardToken;
import com.empresa.cardtransactionsystem.domain.model.SagaPayload;
import com.empresa.cardtransactionsystem.domain.model.TransactionStatus;
import com.empresa.cardtransactionsystem.domain.ports.input.ValidateTransactionUseCase;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ValidateTransactionFunction")
class ValidateTransactionFunctionTest {

    @Mock private ValidateTransactionUseCase validateTransactionUseCase;
    @Mock private TraceparentExtractor traceparentExtractor;

    private Consumer<SagaPayload> consumer;

    @BeforeEach
    void setUp() {
        lenient().when(traceparentExtractor.restore(any())).thenReturn(Scope.noop());
        consumer = new FunctionsConfig(OpenTelemetry.noop(), new SimpleMeterRegistry())
                .validateTransactionFunction(validateTransactionUseCase, traceparentExtractor);
    }

    @Test
    @DisplayName("should delegate validation to use case")
    void shouldDelegateToUseCase() {
        SagaPayload payload = new SagaPayload("TXN-001", UUID.randomUUID(), new CardToken("tok"),
                new BigDecimal("500.00"), 1, Brand.VISA,
                TransactionStatus.PENDING, LocalDateTime.now(), null);

        consumer.accept(payload);

        verify(validateTransactionUseCase).validate(payload);
    }
}
