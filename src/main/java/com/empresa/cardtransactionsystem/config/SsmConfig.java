package com.empresa.cardtransactionsystem.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;

@Configuration
@Profile("!local")
public class SsmConfig {

    @Bean
    public SsmClient ssmClient(@Value("${aws.region:sa-east-1}") String region) {
        return SsmClient.builder()
                .region(Region.of(region))
                .build();
    }
}
