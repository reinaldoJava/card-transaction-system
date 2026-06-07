package com.empresa.cardtransactionsystem.domain.model;

import java.util.UUID;

public record TransactionResult(
        UUID correlationId,
        TransactionStatus status,
        String reason
) {
    public static TransactionResult approved(UUID correlationId) {
        return new TransactionResult(correlationId, TransactionStatus.APPROVED, null);
    }

    public static TransactionResult rejected(UUID correlationId, String reason) {
        return new TransactionResult(correlationId, TransactionStatus.REJECTED, reason);
    }

    public static TransactionResult timeout(UUID correlationId) {
        return new TransactionResult(correlationId, TransactionStatus.TIMEOUT, "Processing timeout");
    }
}
