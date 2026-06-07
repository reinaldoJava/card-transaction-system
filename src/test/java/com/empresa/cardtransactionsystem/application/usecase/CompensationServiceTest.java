package com.empresa.cardtransactionsystem.application.usecase;

import com.empresa.cardtransactionsystem.domain.model.TransactionStatus;
import com.empresa.cardtransactionsystem.domain.ports.output.TransactionRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("CompensationService")
class CompensationServiceTest {

    @Mock private TransactionRepositoryPort repositoryPort;
    private CompensationService service;

    @BeforeEach
    void setUp() {
        service = new CompensationService(repositoryPort);
    }

    @Test
    @DisplayName("should update transaction status to REJECTED")
    void shouldUpdateStatusToRejected() {
        UUID uuid = UUID.randomUUID();
        service.compensate(uuid);
        verify(repositoryPort).updateStatus(uuid, TransactionStatus.REJECTED);
    }
}
