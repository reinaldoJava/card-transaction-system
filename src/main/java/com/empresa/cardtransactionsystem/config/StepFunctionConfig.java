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
@Profile("saga-stepfunctions")
public class StepFunctionConfig {

    @Bean
    @Profile("env-local")
    public SfnClient stepFunctionsClient(
            @Value("${cloud.aws.endpoint.uri:http://localhost:4566}") String endpoint) {
        return SfnClient.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test", "test")))
                .build();
    }

    @Bean
    @Profile("env-aws")
    public SfnClient stepFunctionsClientAws() {
        return SfnClient.builder().build();
    }
}
