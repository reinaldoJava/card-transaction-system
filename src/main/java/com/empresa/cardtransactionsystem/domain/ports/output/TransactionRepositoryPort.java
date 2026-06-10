package com.empresa.cardtransactionsystem.domain.ports.output;

import com.empresa.cardtransactionsystem.domain.model.SagaPayload;
import com.empresa.cardtransactionsystem.domain.model.TransactionStatus;

import java.util.Optional;
import java.util.UUID;

public interface TransactionRepositoryPort {
    void save(SagaPayload payload);
    void updateStatus(UUID correlationId, TransactionStatus status);
    void updateStatusAndReason(UUID correlationId, TransactionStatus status, String reason);
    Optional<TransactionStatu