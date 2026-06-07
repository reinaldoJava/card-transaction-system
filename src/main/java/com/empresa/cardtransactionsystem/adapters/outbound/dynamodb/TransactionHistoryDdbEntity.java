package com.empresa.cardtransactionsystem.adapters.outbound.dynamodb;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.math.BigDecimal;

@DynamoDbBean
public class TransactionHistoryDdbEntity {
    private String cardToken;
    private String recentTransactions;
    private int velocityLast24h;
    private BigDecimal totalAmountLast30Days;

    @DynamoDbPartitionKey
    public String getCardToken() {
        return cardToken;
    }

    public void setCardToken(String cardToken) {
        this.cardToken = cardToken;
    }

    public String getRecentTransactions() {
        return recentTransactions;
    }

    public void setRecentTransactions(String recentTransactions) {
        this.recentTransactions = recentTransactions;
    }

    public int getVelocityLast24h() {
        return velocityLast24h;
    }

    public void setVelocityLast24h(int velocityLast24h) {
        this.velocityLast24h = velocityLast24h;
    }

    public BigDecimal getTotalAmountLast30Days() {
        return totalAmountLast30Days;
    }

    public void setTotalAmountLast30Days(BigDecimal totalAmountLast30Days) {
        this.totalAmountLast30Days = totalAmountLast30Days;
    }
}
