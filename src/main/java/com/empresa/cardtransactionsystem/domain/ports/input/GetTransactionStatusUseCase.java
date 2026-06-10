package com.empresa.cardtransactionsystem.domain.ports.input;

import com.empresa.cardtransactionsystem.domain.model.TransactionResult;

import java.util.Optional;
import java.util.UUID;

public interface GetTransactionStatusUseCase {
    Optional<TransactionResult> getStatus(UUID correlationId);
}
