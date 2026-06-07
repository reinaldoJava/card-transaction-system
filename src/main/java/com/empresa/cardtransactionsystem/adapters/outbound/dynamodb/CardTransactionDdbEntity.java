package com.empresa.cardtransactionsystem.adapters.outbound.dynamodb;

import com.empresa.cardtransactionsystem.domain.model.Brand;
import com.empresa.cardtransactionsystem.domain.model.CardToken;
import com.empresa.cardtransactionsystem.domain.model.SagaPayload;
import com.empresa.cardtransactionsystem.domain.model.TransactionStatus;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@DynamoDbBean
public class CardTransactionDdbEntity {
    private String uuidTransaction;
    private String transactionId;
    private String cardToken;
    private BigDecimal amount;
    private int installments;
    private String brand;
    private String status;
    private String createdAt;

    @DynamoDbPartitionKey
    public String getUuidTransaction() { return uuidTransaction; }
    public void setUuidTransaction(String uuidTransaction) { this.uuidTransaction = uuidTransaction; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getCardToken() { return cardToken; }
    public void setCardToken(String cardToken) { this.cardToken = cardToken; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public int getInstallments() { return installments; }
    public void setInstallments(int installments) { this.installments = installments; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public static CardTransactionDdbEntity from(SagaPayload payload) {
        CardTransactionDdbEntity entity = new CardTransactionDdbEntity();
        entity.setUuidTransaction(payload.correlationId().toString());
        entity.setTransactionId(payload.transactionId());
        entity.setCardToken(payload.cardToken().value());
        entity.setAmount(payload.amount());
        entity.setInstallments(payload.installments());
        entity.setBrand(payload.brand().name());
        entity.setStatus(payload.status().name());
        entity.setCreatedAt(payload.createdAt().format(DateTimeFormatter.ISO_DATE_TIME));
        return entity;
    }

    public SagaPayload toDomain() {
        return new SagaPayload(
                transactionId,
                UUID.fromString(uuidTransaction),
                new CardToken(cardToken),
                amount,
                installments,
                Brand.valueOf(brand),
                TransactionStatus.valueOf(status),
                LocalDateTime.parse(createdAt, DateTimeFormatter.ISO_DATE_TIME),
                null
        );
    }
}
