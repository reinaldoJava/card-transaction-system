package com.empresa.cardtransactionsystem.adapters.outbound.dynamodb.entity;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

@DynamoDbBean
public class CacheDdbEntity {
    private String cacheKey;
    private String value;
    private long expiresAt;

    @DynamoDbPartitionKey
    public String getCacheKey() {
        return cacheKey;
    }

    public void setCacheKey(String cacheKey) {
        this.cacheKey = cacheKey;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(long expiresAt) {
        this.expiresAt = expiresAt;
    }


    public CacheDdbEntity() {}

    public CacheDdbEntity(String cacheKey, String value, long expiresAt) {
        this.cacheKey = cacheKey;
        this.value = value;
        this.expiresAt = expiresAt;
    }
}
