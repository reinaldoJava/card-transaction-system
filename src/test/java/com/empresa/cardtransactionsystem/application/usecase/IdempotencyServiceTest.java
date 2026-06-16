package com.empresa.cardtransactionsystem.application.usecase;

import com.empresa.cardtransactionsystem.domain.model.TransactionResult;
import com.empresa.cardtransactionsystem.domain.model.TransactionStatus;
import com.empresa.cardtransactionsystem.domain.ports.output.CachePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("IdempotencyService")
class IdempotencyServiceTest {

    @Mock private CachePort cachePort;
    private IdempotencyService service;

    @BeforeEach
    void setUp() {
        service = new IdempotencyService(cachePort);
    }

    @Test
    @DisplayName("should return empty when transactionId was never processed")
    void shouldReturnEmptyForNewTransaction() {
        when(cachePort.getIdempotencyResult("TXN-001")).thenReturn(Optional.empty());
        assertThat(service.check("TXN-001")).isEmpty();
    }

    @Test
    @DisplayName("should return cached result for already processed transactionId")
    void shouldReturnCachedResult() {
        TransactionResult result = TransactionResult.approved(UUID.randomUUID());
        when(cachePort.getIdempotencyResult("TXN-001")).thenReturn(Optional.of(result));
        assertThat(service.check("TXN-001")).isPresent()
                .hasValueSatisfying(r -> assertThat(r.status()).isEqualTo(TransactionStatus.APPROVED));
    }

    @Test
    @DisplayName("should store APPROVED result in cache")
    void shouldStoreApprovedResultInCache() {
        TransactionResult result = TransactionResult.approved(UUID.randomUUID());
        service.store("TXN-001", result);
        verify(cachePort).putIdempotencyResult("TXN-001", result);
    }

    @Test
    @DisplayName("should store REJECTED result in cache to prevent saga re-execution")
    void shouldStoreRejectedResultInCache() {
        TransactionResult rejected = TransactionResult.rejected(UUID.randomUUID(), "Insufficient credit");
        service.store("TXN-001", rejected);
        verify(cachePort).putIdempotencyResult("TXN-001", rejected);
    }

    @Test
    @DisplayName("should not store TIMEOUT result")
    void shouldNotStoreTimeoutResult() {
        TransactionResult timeout = TransactionResult.timeout(UUID.randomUUID());
        service.store("TXN-001", timeout);
        verify(cachePort, never()).putIdempotencyResult("TXN-001", timeout);
    }
}
