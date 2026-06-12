package com.empresa.cardtransactionsystem.adapters.outbound.dynamodb;

import com.empresa.cardtransactionsystem.adapters.outbound.dynamodb.entity.UserDdbEntity;
import com.empresa.cardtransactionsystem.domain.model.auth.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;

import java.net.URI;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamoDbUserRepositoryAdapterTest {

    private DynamoDbUserRepositoryAdapter adapter;
    private DynamoDbClient dynamoDbClient;
    private DynamoDbEnhancedClient enhancedClient;

    @BeforeEach
    void setUp() {
        dynamoDbClient = createLocalDynamoDbClient();
        deleteTableIfExists(dynamoDbClient, "users");
        createTable(dynamoDbClient);
        enhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
        adapter = new DynamoDbUserRepositoryAdapter(enhancedClient);
    }

    @Test
    void shouldFindUserByUsername() {
        User user = new User("john.doe", "hashed_password_123");
        UserDdbEntity entity = UserDdbEntity.fromDomain(user);
        putItem(entity);

        Optional<User> found = adapter.findByUsername("john.doe");

        assertTrue(found.isPresent());
        assertEquals("john.doe", found.get().username());
        assertEquals("hashed_password_123", found.get().hashedPassword());
    }

    @Test
    void shouldReturnEmptyWhenUserNotFound() {
        Optional<User> found = adapter.findByUsername("nonexistent.user");
        assertTrue(found.isEmpty());
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
                .tableName("users")
                .keySchema(KeySchemaElement.builder()
                        .attributeName("username")
                        .keyType(KeyType.HASH)
                        .build())
                .attributeDefinitions(AttributeDefinition.builder()
                        .attributeName("username")
                        .attributeType(ScalarAttributeType.S)
                        .build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .build();

        client.createTable(request);
    }

    private void putItem(UserDdbEntity entity) {
        software.amazon.awssdk.services.dynamodb.model.PutItemRequest request =
                software.amazon.awssdk.services.dynamodb.model.PutItemRequest.builder()
                        .tableName("users")
                        .item(
                                java.util.Map.of(
                                        "username", software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder().s(entity.getUsername()).build(),
                                        "hashedPassword", software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder().s(entity.getHashedPassword()).build()
                                )
                        )
                        .build();
        dynamoDbClient.putItem(request);
    }
}
