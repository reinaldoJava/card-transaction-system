package com.empresa.cardtransactionsystem.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;

import java.net.URI;

@Configuration
public class SsmConfig {

    @Bean
    @Profile("env-local")
    public SsmClient localSsmClient(
            @Value("${cloud.aws.endpoint.uri:http://localhost:4566}") String endpoint) {
        return SsmClient.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test", "test")))
                .build();
    }

    @Bean
    @Profile("env-aws")
    public SsmClient awsSsmClient() {
        return SsmClient.builder().build();
    }
}
