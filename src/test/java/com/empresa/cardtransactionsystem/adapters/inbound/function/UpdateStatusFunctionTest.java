package com.empresa.cardtransactionsystem.adapters.inbound.function;

import com.empresa.cardtransactionsystem.adapters.outbound.observability.TraceparentExtractor;
import com.empresa.cardtransactionsystem.adapters.outbound.observability.TransactionMetrics;
import com.empresa.cardtransactionsystem.application.usecase.IdempotencyService;
import com.empresa.cardtransactionsystem.domain.model.Brand;
import com.empresa.cardtransactionsystem.domain.model.CardToken;
import com.empresa.cardtransactionsystem.domain.model.FraudScore;
import com.empresa.cardtransactionsystem.domain.model.SagaPayload;
import com.empresa.cardtransactionsystem.domain.model.TransactionStatus;
import com.empresa.cardtransactionsystem.domain.ports.input.CompensationUseCase;
import com.empresa.cardtransactionsystem.domain.ports.output.CallbackNotifierPort;
import com.empresa.cardtransactionsystem.domain.ports.output.DomainEventPublisherPort;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UpdateStatusFunction")
class UpdateStatusFunctionTest {

    @Mock private TransactionRepositoryPort repositoryPort;
    @Mock private DomainEventPublisherPort eventPublisher;
    @Mock private CompensationUseCase compensationUseCase;
    @Mock private IdempotencyService idempotencyService;
    @Mock private CallbackNotifierPort callbackNotifier;
    @Mock private TraceparentExtractor traceparentExtractor;
    @Mock private TransactionMetrics metrics;

    private Consumer<FraudResult> function;

    @BeforeEach
    void setUp() {
        SagaPayload stub = new SagaPayload("TXN-STUB", UUID.randomUUID(), new CardToken("tok-stub"),
                BigDecimal.TEN, 1, Brand.VISA, TransactionStatus.PENDING, LocalDateTime.now(), null, null);
        lenient().when(traceparentExtractor.restore(any())).thenReturn(Scope.noop());
        lenient().when(repositoryPort.findById(any())).thenReturn(Optional.of(stub));
        function = new FunctionsConfig(OpenTelemetry.noop(), new SimpleMeterRegistry())
                .updateStatusFunction(repositoryPort, eventPublisher, compensationUseCase,
                        idempotencyService, callbackNotifier, traceparentExtractor, metrics, 80);
    }

    @Test
    @DisplayName("should approve transaction and publish event when score is below threshold")
    void shouldApproveWhenScoreBelowThreshold() {
        UUID uuid = UUID.randomUUID();

        function.accept(new FraudResult("TXN-001", uuid, FraudScore.of(50), null, null));

        verify(repositoryPort).updateStatus(uuid, TransactionStatus.APPROVED);
        verify(eventPublisher).publish(any());
    }

    @Test
    @DisplayName("should compensate and publish event when score meets or exceeds threshold")
    void shouldCompensateWhenScoreExceedsThreshold() {
        UUID uuid = UUID.randomUUID();

        function.accept(new FraudResult("TXN-001", uuid, FraudScore.of(85), null, null));

        verify(compensationUseCase).compensate(uuid);
        verify(eventPublisher).publish(any());
    }

    @Test
    @DisplayName("should not publish event when transaction not found in repository")
    void shouldNotPublishWhenTransactionNotFound() {
        UUID uuid = UUID.randomUUID();
        when(repositoryPort.findById(uuid)).thenReturn(Optional.empty());

        function.accept(new FraudResult("TXN-002", uuid, FraudScore.of(50), null, null));

        verify(repositoryPort).updateStatus(eq(uuid), any());
        verify(eventPublisher, org.mockito.Mockito.never()).publish(any());
    }
}
