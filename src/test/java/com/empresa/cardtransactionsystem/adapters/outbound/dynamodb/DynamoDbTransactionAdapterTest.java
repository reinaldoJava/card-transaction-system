package com.empresa.cardtransactionsystem.adapters.outbound.dynamodb;

import com.empresa.cardtransactionsystem.domain.model.Brand;
import com.empresa.cardtransactionsystem.domain.model.CardToken;
import com.empresa.cardtransactionsystem.domain.model.SagaPayload;
import com.empresa.cardtransactionsystem.domain.model.TransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamoDbTransactionAdapterTest {

    private DynamoDbTransactionAdapter adapter;
    private DynamoDbClient dynamoDbClient;

    @BeforeEach
    void setUp() {
        dynamoDbClient = DynamoDbClient.builder()
                .endpointOverride(URI.create("http://localhost:4566"))
                .region(software.amazon.awssdk.regions.Region.US_EAST_1)
                .credentialsProvider(software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(
                        software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create("test", "test")))
                .build();
        deleteTableIfExists(dynamoDbClient, "card-transactions");
        createTable(dynamoDbClient);
        DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient).build();
        adapter = new DynamoDbTransactionAdapter(enhancedClient);
    }

    @Test
    void shouldSaveAndRetrieveTransaction() {
        UUID correlationId = UUID.randomUUID();
        SagaPayload payload = new SagaPayload("TXN001", correlationId,
                new CardToken("safe-token"), new BigDecimal("100.00"), 1, Brand.VISA,
                TransactionStatus.PENDING, LocalDateTime.now(), null);

        adapter.save(payload);

        Optional<TransactionStatus> status = adapter.findStatus(correlationId);
        assertTrue(status.isPresent());
        assertEquals(TransactionStatus.PENDING, status.get());
    }

    @Test
    void shouldUpdateTransactionStatus() {
        UUID correlationId = UUID.randomUUID();
        SagaPayload payload = new SagaPayload("TXN002", correlationId,
                new CardToken("safe-token"), new BigDecimal("200.00"), 2, Brand.VISA,
                TransactionStatus.PENDING, LocalDateTime.now(), null);

        adapter.save(payload);
        adapter.updateStatus(correlationId, TransactionStatus.APPROVED);

        Optional<TransactionStatus> status = adapter.findStatus(correlationId);
        assertTrue(status.isPresent());
        assertEquals(TransactionStatus.APPROVED, status.get());
    }

    @Test
    void shouldReturnEmptyWhenTransactionNotFound() {
        assertTrue(adapter.findStatus(UUID.randomUUID()).isEmpty());
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
        client.createTable(CreateTableRequest.builder()
                .tableName("card-transactions")
                .keySchema(KeySchemaElement.builder()
                        .attributeName("uuidTransaction").keyType(KeyType.HASH).build())
                .attributeDefinitions(AttributeDefinition.builder()
                        .attributeName("uuidTransaction").attributeType(ScalarAttributeType.S).build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .build());
    }
}
