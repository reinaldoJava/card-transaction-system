package com.empresa.cardtransactionsystem.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sfn.SfnClient;

import java.net.URI;

@Configuration
public class StepFunctionConfig {

    @Bean
    @Profile("local")
    public SfnClient stepFunctionsClient(
            @Value("${aws.dynamodb.endpoint:http://localhost:4566}") String endpoint) {
        return SfnClient.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test", "test")))
                .build();
    }

    @Bean
    @Profile("!local")
    public SfnClient stepFunctionsClientAws(
            @Value("${aws.region:sa-east-1}") String region) {
        return SfnClient.builder()
                .region(Region.of(region))
                .build();
    }
}
