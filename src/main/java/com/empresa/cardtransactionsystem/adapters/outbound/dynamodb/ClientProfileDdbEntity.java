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
    private String homeLocationCode;

    @DynamoDbPartitionKey
    public String getCardToken() { return cardToken; }
    public void setCardToken(String cardToken) { this.cardToken = cardToken; }

    public BigDecimal getCreditLimit() { return creditLimit; }
    public void setCreditLimit(BigDecimal creditLimit) { this.creditLimit = creditLimit; }

    public BigDecimal getUsedCredit() { return usedCredit; }
    public void setUsedCredit(BigDecimal usedCredit) { this.usedCredit = usedCredit; }

    public int getMaxInstallments() { return maxInstallments; }
    public void setMaxInstallments(int maxInstallments) { this.maxInstallments = maxInstallments; }

    public BigDecimal getMonthlyRate() { return monthlyRate; }
    public void setMonthlyRate(BigDecimal monthlyRate) { this.monthlyRate = monthlyRate; }

    public boolean isVip() { return vip; }
    public void setVip(boolean vip) { this.vip = vip; }

    public String getHomeLocationCode() { return homeLocationCode; }
    public void setHomeLocationCode(String homeLocationCode) { this.homeLocationCode = homeLocationCode; }

    public ClientProfile toDomain() {
        return new ClientProfile(creditLimit, usedCredit, maxInstallments, monthlyRate, vip, homeLocationCode);
    }
}
