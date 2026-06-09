package com.empresa.cardtransactionsystem.adapters.outbound.postgres;

import com.empresa.cardtransactionsystem.domain.model.Brand;
import com.empresa.cardtransactionsystem.domain.model.CardToken;
import com.empresa.cardtransactionsystem.domain.model.SagaPayload;
import com.empresa.cardtransactionsystem.domain.model.TransactionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "card_transactions")
public class TransactionEntity {

    @Id
    @Column(name = "correlation_id")
    private UUID correlationId;

    @Column(name = "transaction_id", nullable = false)
    private String transactionId;

    @Column(name = "card_token", nullable = false)
    private String cardToken;

    @Column(name = "amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    @Column(name = "installments", nullable = false)
    private int installments;

    @Column(name = "brand", nullable = false)
    private String brand;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "callback_url")
    private String callbackUrl;

    public static TransactionEntity from(SagaPayload payload) {
        TransactionEntity e = new TransactionEntity();
        e.correlationId = payload.correlationId();
        e.transactionId = payload.transactionId();
        e.cardToken = payload.cardToken().value();
        e.amount = payload.amount();
        e.installments = payload.installments();
        e.brand = payload.brand().name();
        e.status = payload.status().name();
        e.createdAt = payload.createdAt();
        e.callbackUrl = payload.callbackUrl();
        return e;
    }

    public SagaPayload toDomain() {
        return new SagaPayload(
                transactionId, correlationId,
                new CardToken(cardToken), amount, installments,
                Brand.valueOf(brand), TransactionStatus.valueOf(status),
                createdAt, null, callbackUrl);
    }

    public String getCallbackUrl() { return callbackUrl; }

    public UUID getCorrelationId() { return correlationId; }
    public void setCorrelationId(UUID correlationId) { this.correlationId = correlationId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
