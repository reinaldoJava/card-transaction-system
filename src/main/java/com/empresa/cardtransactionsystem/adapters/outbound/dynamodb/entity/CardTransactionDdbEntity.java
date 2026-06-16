package com.empresa.cardtransactionsystem.adapters.outbound.dynamodb.entity;

import com.empresa.cardtransactionsystem.domain.model.Brand;
import com.empresa.cardtransactionsystem.domain.model.CardToken;
import com.empresa.cardtransactionsystem.domain.model.SagaPayload;
import com.empresa.cardtransactionsystem.domain.model.TransactionStatus;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;

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
    private String callbackUrl;
    private String reason;
    private String locationCode;

    @DynamoDbPartitionKey
    public String getUuidTransaction() { return uuidTransaction; }
    public void setUuidTransaction(String uuidTransaction) { this.uuidTransaction = uuidTransaction; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    @DynamoDbSecondaryPartitionKey(indexNames = {"cardToken-index"})
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

    public String getCallbackUrl() { return callbackUrl; }
    public void setCallbackUrl(String callbackUrl) { this.callbackUrl = callbackUrl; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getLocationCode() { return locationCode; }
    public void setLocationCode(String locationCode) { this.locationCode = locationCode; }

    public static CardTransactionDdbEntity from(SagaPayload payload) {
        CardTransactionDdbEntity e = new CardTransactionDdbEntity();
        e.setUuidTransaction(payload.correlationId().toString());
        e.setTransactionId(payload.transactionId());
        e.setCardToken(payload.cardToken().value());
        e.setAmount(payload.amount());
        e.setInstallments(payload.installments());
        e.setBrand(payload.brand().name());
        e.setStatus(payload.status().name());
        e.setCreatedAt(payload.createdAt().format(DateTimeFormatter.ISO_DATE_TIME));
        e.setCallbackUrl(payload.callbackUrl());
        e.setLocationCode(payload.locationCode());
        return e;
    }

    public SagaPayload toDomain() {
        return new SagaPayload(
                transactionId,
                UUID.fromString(uuidTransaction),
                new CardToken(cardToken),
                amount, installments,
                Brand.valueOf(brand),
                TransactionStatus.valueOf(status),
                LocalDateTime.parse(createdAt, DateTimeFormatter.ISO_DATE_TIME),
                null, callbackUrl, locationCode);
    }
}
