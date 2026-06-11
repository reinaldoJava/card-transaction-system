package com.empresa.cardtransactionsystem.adapters.outbound.redis;

import com.empresa.cardtransactionsystem.domain.model.CardToken;
import com.empresa.cardtransactionsystem.domain.model.ClientProfile;
import com.empresa.cardtransactionsystem.domain.model.FraudScore;
import com.empresa.cardtransactionsystem.domain.model.TransactionResult;
import com.empresa.cardtransactionsystem.domain.model.TransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class RedisCacheAdapterTest {

    private RedisCacheAdapter adapter;

    @BeforeEach
    void setUp() {
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration("localhost", 6379)
        );
        connectionFactory.afterPropertiesSet();

        StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();

        adapter = new RedisCacheAdapter(redisTemplate, new ObjectMapper());
    }

    @Test
    void shouldStoreAndRetrieveFraudScore() {
        CardToken token = new CardToken("card-abc");
        FraudScore score = new FraudScore(72);

        adapter.putFraudScore(token, score);
        Optional<FraudScore> result = adapter.getFraudScore(token);

        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(72);
    }

    @Test
    void shouldReturnEmptyForMissingFraudScore() {
        Optional<FraudScore> result = adapter.getFraudScore(new CardToken("unknown"));

        assertThat(result).isEmpty();
    }

    @Test
    void shouldStoreAndRetrieveIdempotencyResult() {
        UUID correlationId = UUID.randomUUID();
        String transactionId = UUID.randomUUID().toString();
        TransactionResult transactionResult = new TransactionResult(correlationId, TransactionStatus.APPROVED, null);

        adapter.putIdempotencyResult(transactionId, transactionResult);
        Optional<TransactionResult> result = adapter.getIdempotencyResult(transactionId);

        assertThat(result).isPresent();
        assertThat(result.get().correlationId()).isEqualTo(correlationId);
        assertThat(result.get().status()).isEqualTo(TransactionStatus.APPROVED);
    }

    @Test
    void shouldReturnEmptyForMissingIdempotencyResult() {
        Optional<TransactionResult> result = adapter.getIdempotencyResult("nonexistent-tx");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldStoreAndRetrieveClientProfile() {
        CardToken token = new CardToken("card-xyz");
        ClientProfile profile = new ClientProfile(
                new BigDecimal("15000.00"),
                new BigDecimal("3000.00"),
                12,
                new BigDecimal("0.015"),
                false
        , null);

        adapter.putClientProfile(token, profile);
        Optional<ClientProfile> result = adapter.getClientProfile(token);

        assertThat(result).isPresent();
        assertThat(result.get().creditLimit()).isEqualByComparingTo("15000.00");
        assertThat(result.get().usedCredit()).isEqualByComparingTo("3000.00");
        assertThat(result.get().maxInstallments()).isEqualTo(12);
    }

    @Test
    void shouldReturnEmptyForMissingClientProfile() {
        Optional<ClientProfile> result = adapter.getClientProfile(new CardToken("unknown-card"));

        assertThat(result).isEmpty();
    }

    @Test
    void shouldOverwriteExistingCacheEntry() {
        CardToken token = new CardToken("card-overwrite");
        adapter.putFraudScore(token, new FraudScore(30));
        adapter.putFraudScore(token, new FraudScore(90));

        Optional<FraudScore> result = adapter.getFraudScore(token);

        assertThat(result).isPresent();
        assertThat(result.get().score()).isEqualTo(90);
    }
}
