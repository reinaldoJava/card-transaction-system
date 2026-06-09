package com.empresa.cardtransactionsystem.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import java.net.URI;

@Configuration
public class DynamoDbConfig {

    @Bean
    @Profile("env-local")
    public DynamoDbEnhancedClient localEnhancedClient() {
        DynamoDbClient standardClient = DynamoDbClient.builder()
                .endpointOverride(URI.create("http://localhost:4566"))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test", "test")))
                .build();
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(standardClient)
                .build();
    }

    @Bean
    @Profile("env-aws")
    public DynamoDbEnhancedClient awsEnhancedClient() {
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(DynamoDbClient.builder().build())
                .build();
    }
}