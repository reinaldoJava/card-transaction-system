package com.empresa.cardtransactionsystem.adapters.outbound.dynamodb;

import com.empresa.cardtransactionsystem.domain.model.ClientProfile;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.math.BigDecimal;

@DynamoDbBean
public class ClientProfileDdbEntity {
    private String cardToken;
    private BigDecimal creditLimit;
    private BigDecimal usedCredit;
    private int maxInstallments;
    private BigDecimal monthlyRate;
    private boolean vip;

    @DynamoDbPartitionKey
    public String getCardToken() {
        return cardToken;
    }

    public void setCardToken(String cardToken) {
        this.cardToken = cardToken;
    }

    public void setCreditLimit(BigDecimal creditLimit) {
        this.creditLimit = creditLimit;
    }

    public void setUsedCredit(BigDecimal usedCredit) {
        this.usedCredit = usedCredit;
    }


    public void setMaxInstallments(int maxInstallments) {
        this.maxInstallments = maxInstallments;
    }

    public void setMonthlyRate(BigDecimal monthlyRate) {
        this.monthlyRate = monthlyRate;
    }

    public boolean isVip() { return vip; }
    public void setVip(boolean vip) { this.vip = vip; }

    public ClientProfile toDomain() {
        return new ClientProfile(creditLimit, usedCredit, maxInstallments, monthlyRate, vip);
    }
}
