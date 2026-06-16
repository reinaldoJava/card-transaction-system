package com.empresa.cardtransactionsystem.config;

import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Configuration
@Profile("env-aws")
public class SsmJwtSecretConfig {

    @Bean
    public SecretKey jwtSecretKey(
            SsmClient ssmClient,
            @Value("${JWT_SECRET_SSM_PATH:/card-transaction-system/jwt-secret}") String ssmPath) {
        String secret = ssmClient.getParameter(
                GetParameterRequest.builder()
                        .name(ssmPath)
                        .withDecryption(true)
                        .build()
        ).parameter().value();
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
