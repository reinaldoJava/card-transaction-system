package com.empresa.cardtransactionsystem.adapters.outbound.redis;

import com.empresa.cardtransactionsystem.domain.model.CardToken;
import com.empresa.cardtransactionsystem.domain.model.ClientProfile;
import com.empresa.cardtransactionsystem.domain.model.FraudScore;
import com.empresa.cardtransactionsystem.domain.model.TransactionResult;
import com.empresa.cardtransactionsystem.domain.ports.output.CachePort;
import io.micrometer.observation.annotation.Observed;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;

@Repository
@Profile("cache-redis")
public class RedisCacheAdapter implements CachePort {

    private static final Duration FRAUD_SCORE_TTL = Duration.ofHours(1);
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final Duration CLIENT_PROFILE_TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisCacheAdapter(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @Observed(name = "cache.fraud_score.get", contextualName = "redis.get-fraud-score-cache")
    public Optional<FraudScore> getFraudScore(CardToken cardToken) {
        return get("FRAUD:" + cardToken.value(), FraudScore.class);
    }

    @Override
    public void putFraudScore(CardToken cardToken, FraudScore score) {
        put("FRAUD:" + cardToken.value(), score, FRAUD_SCORE_TTL);
    }

    @Override
    @Observed(name = "cache.idempotency.get", contextualName = "redis.get-idempotency-cache")
    public Optional<TransactionResult> getIdempotencyResult(String transactionId) {
        return get("IDEMPOTENCY:" + transactionId, TransactionResult.class);
    }

    @Override
    public void putIdempotencyResult(String transactionId, TransactionResult result) {
        put("IDEMPOTENCY:" + transactionId, result, IDEMPOTENCY_TTL);
    }

    @Override
    @Observed(name = "cache.client_profile.get", contextualName = "redis.get-client-profile-cache")
    public Optional<ClientProfile> getClientProfile(CardToken cardToken) {
        return get("PROFILE:" + cardToken.value(), ClientProfile.class);
    }

    @Override
    public void putClientProfile(CardToken cardToken, ClientProfile profile) {
        put("PROFILE:" + cardToken.value(), profile, CLIENT_PROFILE_TTL);
    }

    private <T> Optional<T> get(String key, Class<T> type) {
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, type));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private void put(String key, Object value, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, json, ttl);
        } catch (Exception e) {
            throw new RuntimeException("Failed to write to Redis cache: " + key, e);
        }
    }
}
