package com.empresa.cardtransactionsystem.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record SagaPayload(
        String transactionId,
        UUID correlationId,
        CardToken cardToken,
        BigDecimal amount,
        int installments,
        Brand brand,
        TransactionStatus status,
        LocalDateTime createdAt,
        String traceparent,
        String callbackUrl,
        String locationCode
) {
    public static SagaPayload pending(
            String transactionId, UUID correlationId, CardToken cardToken,
            BigDecimal amount, int installments, Brand brand, String callbackUrl) {
        return new SagaPayload(
                transactionId, correlationId, cardToken,
                amount, installments, brand,
                TransactionStatus.PENDING, LocalDateTime.now(), null, callbackUrl, null);
    }

    public static SagaPayload rejected(
            String transactionId, UUID correlationId, CardToken cardToken,
            BigDecimal amount, int installments, Brand brand, String callbackUrl) {
        return new SagaPayload(
                transactionId, correlationId, cardToken,
                amount, installments, brand,
                TransactionStatus.REJECTED, LocalDateTime.now(), null, callbackUrl, null);
    }

    public SagaPayload withTraceparent(String traceparent) {
        return new SagaPayload(transactionId, correlationId, cardToken,
                amount, installments, brand, status, createdAt, traceparent, callbackUrl, locationCode);
    }

    public SagaPayload withLocationCode(String locationCode) {
        return new SagaPayload(transactionId, correlationId, cardToken,
                amount, installments, brand, status, createdAt, traceparent, callbackUrl, locationCode);
    }
}
