package com.empresa.cardtransactionsystem.application.usecase;

import com.empresa.cardtransactionsystem.domain.model.Brand;
import com.empresa.cardtransactionsystem.domain.model.CardToken;
import com.empresa.cardtransactionsystem.domain.model.SagaPayload;
import com.empresa.cardtransactionsystem.domain.model.TransactionStatus;
import com.empresa.cardtransactionsystem.domain.ports.output.TransactionRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("ValidateCardTransactionUseCase")
class ValidateCardTransactionUseCaseTest {

    @Mock private TransactionRepositoryPort repositoryPort;

    private ValidateCardTransactionUseCase useCase;
    private UUID uuid;

    @BeforeEach
    void setUp() {
        useCase = new ValidateCardTransactionUseCase(repositoryPort);
        uuid = UUID.randomUUID();
    }

    @Test
    @DisplayName("should approve PENDING transaction")
    void shouldApprovePendingTransaction() {
        useCase.validate(payload(TransactionStatus.PENDING));
        verify(repositoryPort).updateStatus(uuid, TransactionStatus.APPROVED);
    }

    @Test
    @DisplayName("should ignore non-PENDING transaction")
    void shouldIgnoreNonPendingTransaction() {
        useCase.validate(payload(TransactionStatus.APPROVED));
        verifyNoInteractions(repositoryPort);
    }

    private SagaPayload payload(TransactionStatus status) {
        return new SagaPayload("TXN-001", uuid, new CardToken("tok"),
                new BigDecimal("500.00"), 1, Brand.VISA, status, LocalDateTime.now(), null, null);
    }
}
