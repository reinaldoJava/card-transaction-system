package com.empresa.cardtransactionsystem.adapters.outbound.dynamodb;

import com.empresa.cardtransactionsystem.domain.model.CardToken;
import com.empresa.cardtransactionsystem.domain.model.ClientProfile;
import com.empresa.cardtransactionsystem.domain.model.FraudScore;
import com.empresa.cardtransactionsystem.domain.model.TransactionResult;
import com.empresa.cardtransactionsystem.domain.ports.output.CachePort;
import io.micrometer.observation.annotation.Observed;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Optional;

@Repository
@Profile("cache-dynamodb")
public class DynamoDbCacheAdapter implements CachePort {

    private final DynamoDbTable<CacheDdbEntity> cacheTable;
    private final ObjectMapper objectMapper;
    private static final long CACHE_TTL_SECONDS = 3600;

    public DynamoDbCacheAdapter(DynamoDbEnhancedClient enhancedClient, ObjectMapper objectMapper) {
        this.cacheTable = enhancedClient.table("cache",
                TableSchema.fromBean(CacheDdbEntity.class));
        this.objectMapper = objectMapper;
    }

    @Override
    @Observed(name = "cache.fraud_score.get", contextualName = "dynamodb.get-fraud-score-cache")
    public Optional<FraudScore> getFraudScore(CardToken cardToken) {
        String cacheKey = "FRAUD#" + cardToken.value();
        CacheDdbEntity entity = cacheTable.getItem(r -> r.key(k -> k.partitionValue(cacheKey)));
        return Optional.ofNullable(entity)
                .flatMap(e -> {
                    try {
                        return Optional.of(objectMapper.readValue(e.getValue(), FraudScore.class));
                    } catch (Exception ex) {
                        return Optional.empty();
                    }
                });
    }

    @Override
    public void putFraudScore(CardToken cardToken, FraudScore score) {
        try {
            String cacheKey = "FRAUD#" + cardToken.value();
            String value = objectMapper.writeValueAsString(score);
            long expiresAt = Instant.now().getEpochSecond() + CACHE_TTL_SECONDS;

            CacheDdbEntity entity = new CacheDdbEntity();
            entity.setCacheKey(cacheKey);
            entity.setValue(value);
            entity.setExpiresAt(expiresAt);

            cacheTable.putItem(entity);
        } catch (Exception e) {
            throw new RuntimeException("Failed to cache fraud score", e);
        }
    }

    @Override
    @Observed(name = "cache.idempotency.get", contextualName = "dynamodb.get-idempotency-cache")
    public Optional<TransactionResult> getIdempotencyResult(String transactionId) {
        String cacheKey = "IDEMPOTENCY#" + transactionId;
        CacheDdbEntity entity = cacheTable.getItem(r -> r.key(k -> k.partitionValue(cacheKey)));
        return Optional.ofNullable(entity)
                .flatMap(e -> {
                    try {
                        return Optional.of(objectMapper.readValue(e.getValue(), TransactionResult.class));
                    } catch (Exception ex) {
                        return Optional.empty();
                    }
                });
    }

    @Override
    public void putIdempotencyResult(String transactionId, TransactionResult result) {
        try {
            String cacheKey = "IDEMPOTENCY#" + transactionId;
            String value = objectMapper.writeValueAsString(result);
            long expiresAt = Instant.now().getEpochSecond() + CACHE_TTL_SECONDS;

            CacheDdbEntity entity = new CacheDdbEntity();
            entity.setCacheKey(cacheKey);
            entity.setValue(value);
            entity.setExpiresAt(expiresAt);

            cacheTable.putItem(entity);
        } catch (Exception e) {
            throw new RuntimeException("Failed to cache transaction result", e);
        }
    }

    @Override
    @Observed(name = "cache.client_profile.get", contextualName = "dynamodb.get-client-profile-cache")
    public Optional<ClientProfile> getClientProfile(CardToken cardToken) {
        String cacheKey = "PROFILE#" + cardToken.value();
        CacheDdbEntity entity = cacheTable.getItem(r -> r.key(k -> k.partitionValue(cacheKey)));
        return Optional.ofNullable(entity)
                .flatMap(e -> {
                    try {
                        return Optional.of(objectMapper.readValue(e.getValue(), ClientProfile.class));
                    } catch (Exception ex) {
                        return Optional.empty();
                    }
                });
    }

    @Override
    public void putClientProfile(CardToken cardToken, ClientProfile profile) {
        try {
            String cacheKey = "PROFILE#" + cardToken.value();
            String value = objectMapper.writeValueAsString(profile);
            long expiresAt = Instant.now().getEpochSecond() + CACHE_TTL_SECONDS;

            CacheDdbEntity entity = new CacheDdbEntity();
            entity.setCacheKey(cacheKey);
            entity.setValue(value);
            entity.setExpiresAt(expiresAt);

            cacheTable.putItem(entity);
        } catch (Exception e) {
            throw new RuntimeException("Failed to cache client profile", e);
        }
    }
}
