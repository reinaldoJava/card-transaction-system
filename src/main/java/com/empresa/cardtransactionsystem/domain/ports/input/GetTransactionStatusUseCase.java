package com.empresa.cardtransactionsystem.domain.ports.input;

import com.empresa.cardtransactionsystem.domain.model.TransactionStatus;

import java.util.Optional;
import java.util.UUID;

public interface GetTransactionStatusUseCase {
    Optional<TransactionStatus> getStatus(UUID correlationId);
}
