package com.empresa.cardtransactionsystem.domain.model;

import java.math.BigDecimal;

public record FraudAnalysisRequest(
        CardToken cardToken,
        BigDecimal amount,
        int installments,
        Brand brand
) {
    public static FraudAnalysisRequest from(SagaPayload payload) {
        return new FraudAnalysisRequest(
                payload.cardToken(),
                payload.amount(),
                payload.installments(),
                payload.brand()
        );
    }
}
