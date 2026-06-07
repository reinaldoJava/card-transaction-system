package com.empresa.cardtransactionsystem.adapters.inbound.function;

import com.empresa.cardtransactionsystem.domain.model.FraudScore;

import java.util.UUID;

public record FraudResult(
        String transactionId,
        UUID uuidTransaction,
        FraudScore fraudScore,
        String traceparent
) {}
