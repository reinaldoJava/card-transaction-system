package com.empresa.cardtransactionsystem.adapters.inbound.function;

import com.empresa.cardtransactionsystem.adapters.outbound.observability.TraceparentExtractor;
import com.empresa.cardtransactionsystem.config.FunctionsConfig;
import com.empresa.cardtransactionsystem.domain.model.SagaPayload;
import com.empresa.cardtransactionsystem.fixture.SagaPayloadFixture;
import com.empresa.cardtransactionsystem.domain.ports.input.CompensationUseCase;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("CompensationFunction")
class CompensationFunctionTest {

    @Mock private CompensationUseCase compensationUseCase;
    @Mock private TraceparentExtractor traceparentExtractor;

    private Consumer<SagaPayload> function;

    @BeforeEach
    void setUp() {
        lenient().when(traceparentExtractor.restore(any())).thenReturn(Scope.noop());
        function = new FunctionsConfig(OpenTelemetry.noop(), new SimpleMeterRegistry())
                .compensationFunction(compensationUseCase, traceparentExtractor);
    }

    @Test
    @DisplayName("should delegate correlationId to use case")
    void shouldDelegateToUseCase() {
        UUID uuid = UUID.randomUUID();
        SagaPayload payload = SagaPayloadFixture.withCorrelationId(uuid);

        function.accept(payload);

        verify(compensationUseCase).compensate(uuid);
    }
}
