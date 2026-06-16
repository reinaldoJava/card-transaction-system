package com.empresa.cardtransactionsystem.fixture;

import com.empresa.cardtransactionsystem.domain.model.Brand;
import com.empresa.cardtransactionsystem.domain.model.CardToken;
import com.empresa.cardtransactionsystem.domain.model.SagaPayload;
import com.empresa.cardtransactionsystem.domain.model.TransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public final class SagaPayloadFixture {

    private SagaPayloadFixture() {}

    public static SagaPayload minimal() {
        return new SagaPayload(
                "TXN-001", UUID.randomUUID(), new CardToken("tok"),
                new BigDecimal("500.00"), 1, Brand.VISA,
                TransactionStatus.PENDING, LocalDateTime.now(),
                null, null, null);
    }

    public static SagaPayload withStatus(TransactionStatus status) {
        return new SagaPayload(
                "TXN-001", UUID.randomUUID(), new CardToken("tok"),
                new BigDecimal("500.00"), 1, Brand.VISA,
                status, LocalDateTime.now(),
                null, null, null);
    }

    public static SagaPayload withAmount(String amount, int installments) {
        return new SagaPayload(
                "TXN-001", UUID.randomUUID(), new CardToken("tok"),
                new BigDecimal(amount), installments, Brand.VISA,
                TransactionStatus.PENDING, LocalDateTime.now(),
                null, null, null);
    }

    public static SagaPayload withCorrelationId(UUID correlationId) {
        return new SagaPayload(
                "TXN-001", correlationId, new CardToken("tok"),
                new BigDecimal("500.00"), 1, Brand.VISA,
                TransactionStatus.PENDING, LocalDateTime.now(),
                null, null, null);
    }

    public static SagaPayload withCardToken(CardToken cardToken) {
        return new SagaPayload(
                "TXN-001", UUID.randomUUID(), cardToken,
                new BigDecimal("500.00"), 1, Brand.VISA,
                TransactionStatus.PENDING, LocalDateTime.now(),
                null, null, null);
    }

    public static SagaPayload withIds(String transactionId, UUID correlationId, CardToken cardToken) {
        return new SagaPayload(
                transactionId, correlationId, cardToken,
                new BigDecimal("500.00"), 1, Brand.VISA,
                TransactionStatus.PENDING, LocalDateTime.now(),
                null, null, null);
    }

    public static SagaPayload withIdsAndStatus(String transactionId, UUID correlationId, CardToken cardToken, TransactionStatus status) {
        return new SagaPayload(
                transactionId, correlationId, cardToken,
                new BigDecimal("500.00"), 1, Brand.VISA,
                status, LocalDateTime.now(),
                null, null, null);
    }
}
