package com.empresa.cardtransactionsystem.adapters.outbound.dynamodb;

import com.empresa.cardtransactionsystem.domain.model.CardToken;
import com.empresa.cardtransactionsystem.domain.model.ClientProfile;
import com.empresa.cardtransactionsystem.domain.model.FraudScore;
import com.empresa.cardtransactionsystem.domain.model.TransactionResult;
import com.empresa.cardtransactionsystem.domain.model.TransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamoDbCacheAdapterTest {

    private DynamoDbCacheAdapter adapter;
    private DynamoDbClient dynamoDbClient;
    private DynamoDbEnhancedClient enhancedClient;
    private final UUID uuid = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        dynamoDbClient = createLocalDynamoDbClient();
        deleteTableIfExists(dynamoDbClient, "cache");
        createTable(dynamoDbClient);
        enhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
        adapter = new DynamoDbCacheAdapter(enhancedClient, new ObjectMapper());
    }

    @Test
    void shouldCacheAndRetrieveFraudScore() {
        CardToken cardToken = new CardToken("token123");
        FraudScore fraudScore = new FraudScore(75);

        adapter.putFraudScore(cardToken, fraudScore);
        Optional<FraudScore> retrieved = adapter.getFraudScore(cardToken);

        assertTrue(retrieved.isPresent());
        assertEquals(75, retrieved.get().score());
    }

    @Test
    void shouldCacheAndRetrieveIdempotencyResult() {
        TransactionResult result = new TransactionResult(
                uuid,
                TransactionStatus.APPROVED,
                "Approved"
        );

        adapter.putIdempotencyResult(uuid.toString(), result);
        Optional<TransactionResult> retrieved = adapter.getIdempotencyResult(uuid.toString());

        assertTrue(retrieved.isPresent());
        assertEquals(uuid, retrieved.get().correlationId());
        assertEquals(TransactionStatus.APPROVED, retrieved.get().status());
    }

    @Test
    void shouldCacheAndRetrieveClientProfile() {
        CardToken cardToken = new CardToken("token456");
        ClientProfile profile = new ClientProfile(
                new BigDecimal("10000.00"),
                new BigDecimal("2000.00"),
                24,
                new BigDecimal("0.01"),
                false
        );

        adapter.putClientProfile(cardToken, profile);
        Optional<ClientProfile> retrieved = adapter.getClientProfile(cardToken);

        assertTrue(retrieved.isPresent());
        assertEquals(new BigDecimal("10000.00"), retrieved.get().creditLimit());
        assertEquals(new BigDecimal("2000.00"), retrieved.get().usedCredit());
    }

    @Test
    void shouldReturnEmptyWhenCacheKeyNotFound() {
        CardToken cardToken = new CardToken("nonexistent");

        Optional<FraudScore> fraudScore = adapter.getFraudScore(cardToken);
        Optional<ClientProfile> profile = adapter.getClientProfile(cardToken);

        assertTrue(fraudScore.isEmpty());
        assertTrue(profile.isEmpty());
    }

    private DynamoDbClient createLocalDynamoDbClient() {
        return DynamoDbClient.builder()
                .endpointOverride(URI.create("http://localhost:4566"))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test", "test")
                ))
                .build();
    }

    private void deleteTableIfExists(DynamoDbClient client, String tableName) {
        try {
            client.deleteTable(DeleteTableRequest.builder().tableName(tableName).build());
            Thread.sleep(1000);
        } catch (ResourceNotFoundException ignored) {
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void createTable(DynamoDbClient client) {
        CreateTableRequest request = CreateTableRequest.builder()
                .tableName("cache")
                .keySchema(KeySchemaElement.builder()
                        .attributeName("cacheKey")
                        .keyType(KeyType.HASH)
                        .build())
                .attributeDefinitions(AttributeDefinition.builder()
                        .attributeName("cacheKey")
                        .attributeType(ScalarAttributeType.S)
                        .build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .build();

        client.createTable(request);
    }
}
