package com.empresa.cardtransactionsystem.adapters.inbound.kafka.dto;

import com.empresa.cardtransactionsystem.domain.model.Brand;
import com.empresa.cardtransactionsystem.domain.model.CardToken;
import com.empresa.cardtransactionsystem.domain.model.SagaPayload;
import com.empresa.cardtransactionsystem.domain.model.TransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransactionAuditEvent(
        String transactionId,
        String correlationId,
        String cardToken,
        BigDecimal amount,
        int installments,
        String brand,
        String status,
        LocalDateTime createdAt
) {
    public SagaPayload toSagaPayload() {
        return new SagaPayload(
                transactionId,
                UUID.fromString(correlationId),
                new CardToken(cardToken),
                amount,
                installments,
                Brand.valueOf(brand),
                TransactionStatus.valueOf(status),
                createdAt != null ? createdAt : LocalDateTime.now(),
                null,
                null,
                null
        );
    }
}
