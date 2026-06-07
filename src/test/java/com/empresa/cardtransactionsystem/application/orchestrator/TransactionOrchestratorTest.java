package com.empresa.cardtransactionsystem.application.orchestrator;

import com.empresa.cardtransactionsystem.adapters.inbound.rest.dto.CardDataRequest;
import com.empresa.cardtransactionsystem.adapters.inbound.rest.dto.CardTransactionRequest;
import com.empresa.cardtransactionsystem.application.usecase.IdempotencyService;
import com.empresa.cardtransactionsystem.domain.model.CardData;
import com.empresa.cardtransactionsystem.domain.model.CardToken;
import com.empresa.cardtransactionsystem.domain.model.FraudScore;
import com.empresa.cardtransactionsystem.domain.model.TransactionResult;
import com.empresa.cardtransactionsystem.domain.model.TransactionStatus;
import com.empresa.cardtransactionsystem.adapters.outbound.observability.TraceparentExtractor;
import com.empresa.cardtransactionsystem.adapters.outbound.observability.TransactionMetrics;
import com.empresa.cardtransactionsystem.domain.ports.output.CachePort;
import com.empresa.cardtransactionsystem.domain.ports.output.SagaStarterPort;
import com.empresa.cardtransactionsystem.domain.ports.output.TransactionRepositoryPort;
import com.empresa.cardtransactionsystem.domain.service.CardValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionOrchestrator")
class TransactionOrchestratorTest {

    @Mock private SagaStarterPort sagaStarterPort;
    @Mock private TransactionRepositoryPort transactionRepository;
    @Mock private CardValidationService cardValidationService;
    @Mock private IdempotencyService idempotencyService;
    @Mock private CachePort cachePort;
    @Mock private TraceparentExtractor traceparentExtractor;
    @Mock private TransactionMetrics metrics;

    private TransactionOrchestrator orchestrator;

    private static final CardToken TOKEN = new CardToken("token-uuid");

    @BeforeEach
    void setUp() {
        orchestrator = new TransactionOrchestrator(
                sagaStarterPort, transactionRepository,
                cardValidationService, idempotencyService, cachePort,
                traceparentExtractor, metrics, 80);
        lenient().when(cardValidationService.tokenize(any(CardData.class))).thenReturn(TOKEN);
        lenient().when(idempotencyService.check(anyString())).thenReturn(Optional.empty());
        lenient().when(cachePort.getFraudScore(any())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("should return correlationId from cache when idempotency hit")
    void shouldReturnCachedCorrelationIdOnIdempotencyHit() {
        var uuid = UUID.randomUUID();
        when(idempotencyService.check("TXN-001")).thenReturn(Optional.of(TransactionResult.approved(uuid)));

        UUID result = orchestrator.orchestrate(buildRequest(uuid));

        assertThat(result).isEqualTo(uuid);
        verify(sagaStarterPort, never()).start(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("should save REJECTED payload and not start saga when fraud score exceeds threshold")
    void shouldRejectImmediatelyWhenFraudScoreExceedsThreshold() {
        var uuid = UUID.randomUUID();
        when(cachePort.getFraudScore(TOKEN)).thenReturn(Optional.of(FraudScore.of(85)));

        UUID result = orchestrator.orchestrate(buildRequest(uuid));

        assertThat(result).isEqualTo(uuid);
        verify(transactionRepository).save(any());
        verify(sagaStarterPort, never()).start(any());
        verify(idempotencyService).store(eq("TXN-001"), any(TransactionResult.class));
    }

    @Test
    @DisplayName("should save PENDING payload and start saga for valid request")
    void shouldSavePendingAndStartSaga() {
        var uuid = UUID.randomUUID();

        UUID result = orchestrator.orchestrate(buildRequest(uuid));

        assertThat(result).isEqualTo(uuid);
        verify(transactionRepository).save(any());
        verify(sagaStarterPort).start(any());
    }

    @Test
    @DisplayName("should return correlationId from request without blocking on DynamoDB")
    void shouldReturnCorrelationIdImmediately() {
        var uuid = UUID.randomUUID();

        UUID result = orchestrator.orchestrate(buildRequest(uuid));

        assertThat(result).isEqualTo(uuid);
        verify(transactionRepository, never()).findStatus(any());
    }

    @Test
    @DisplayName("should not store idempotency for normal saga flow at initiation time")
    void shouldNotStoreIdempotencyAtInitiationForNormalFlow() {
        var uuid = UUID.randomUUID();

        orchestrator.orchestrate(buildRequest(uuid));

        verify(idempotencyService, never()).store(anyString(), any(TransactionResult.class));
    }

    private CardTransactionRequest buildRequest(UUID uuid) {
        return new CardTransactionRequest(
                "TXN-001", uuid,
                new CardDataRequest("4111111111111111", "123", "John Doe", "VISA"),
                new BigDecimal("500.00"), 3
        );
    }
}