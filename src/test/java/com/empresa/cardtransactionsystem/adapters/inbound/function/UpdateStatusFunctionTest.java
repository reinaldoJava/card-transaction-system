package com.empresa.cardtransactionsystem.adapters.inbound.function;

import com.empresa.cardtransactionsystem.adapters.outbound.observability.TraceparentExtractor;
import com.empresa.cardtransactionsystem.adapters.outbound.observability.TransactionMetrics;
import com.empresa.cardtransactionsystem.application.usecase.IdempotencyService;
import com.empresa.cardtransactionsystem.domain.model.FraudScore;
import com.empresa.cardtransactionsystem.domain.model.TransactionStatus;
import com.empresa.cardtransactionsystem.domain.ports.input.CompensationUseCase;
import com.empresa.cardtransactionsystem.domain.ports.output.TransactionRepositoryPort;
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
@DisplayName("UpdateStatusFunction")
class UpdateStatusFunctionTest {

    @Mock private TransactionRepositoryPort repositoryPort;
    @Mock private CompensationUseCase compensationUseCase;
    @Mock private IdempotencyService idempotencyService;
    @Mock private TraceparentExtractor traceparentExtractor;
    @Mock private TransactionMetrics metrics;

    private Consumer<FraudResult> function;

    @BeforeEach
    void setUp() {
        lenient().when(traceparentExtractor.restore(any())).thenReturn(Scope.noop());
        function = new FunctionsConfig(OpenTelemetry.noop(), new SimpleMeterRegistry())
                .updateStatusFunction(repositoryPort, compensationUseCase, idempotencyService, traceparentExtractor, metrics, 80);
    }

    @Test
    @DisplayName("should approve transaction when score is below threshold")
    void shouldApproveWhenScoreBelowThreshold() {
        UUID uuid = UUID.randomUUID();

        function.accept(new FraudResult("TXN-001", uuid, FraudScore.of(50), null));

        verify(repositoryPort).updateStatus(uuid, TransactionStatus.APPROVED);
    }

    @Test
    @DisplayName("should compensate when score meets or exceeds threshold")
    void shouldCompensateWhenScoreExceedsThreshold() {
        UUID uuid = UUID.randomUUID();

        function.accept(new FraudResult("TXN-001", uuid, FraudScore.of(85), null));

        verify(compensationUseCase).compensate(uuid);
    }
}
