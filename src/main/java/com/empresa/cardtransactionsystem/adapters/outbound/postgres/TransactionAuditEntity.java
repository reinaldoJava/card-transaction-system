package com.empresa.cardtransactionsystem.adapters.outbound.postgres;

import com.empresa.cardtransactionsystem.domain.model.Brand;
import com.empresa.cardtransactionsystem.domain.model.CardToken;
import com.empresa.cardtransactionsystem.domain.model.SagaPayload;
import com.empresa.cardtransactionsystem.domain.model.TransactionStatus;
import com.empresa.cardtransactionsystem.domain.model.TransactionSummary;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transaction_audit")
public class TransactionAuditEntity {

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

    public static TransactionAuditEntity from(SagaPayload payload) {
        TransactionAuditEntity e = new TransactionAuditEntity();
        e.correlationId = payload.correlationId();
        e.transactionId = payload.transactionId();
        e.cardToken = payload.cardToken().value();
        e.amount = payload.amount();
        e.installments = payload.installments();
        e.brand = payload.brand().name();
        e.status = payload.status().name();
        e.createdAt = payload.createdAt();
        return e;
    }

    public TransactionSummary toSummary() {
        return new TransactionSummary(correlationId, amount, TransactionStatus.valueOf(status), createdAt);
    }

    public SagaPayload toDomain() {
        return new SagaPayload(
                transactionId, correlationId,
                new CardToken(cardToken), amount, installments,
                Brand.valueOf(brand), TransactionStatus.valueOf(status),
                createdAt, null, null,null);
    }

    public UUID getCorrelationId() { return correlationId; }
    public String getCardToken() { return cardToken; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public BigDecimal getAmount() { return amount; }
}
