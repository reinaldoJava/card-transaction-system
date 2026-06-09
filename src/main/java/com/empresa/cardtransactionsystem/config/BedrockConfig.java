package com.empresa.cardtransactionsystem.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

@Configuration
@Profile("fraud-bedrock")
public class BedrockConfig {

    @Bean
    public BedrockRuntimeClient bedrockRuntimeClient() {
        return BedrockRuntimeClient.builder()
                .region(Region.SA_EAST_1)
                .build();
    }
}
