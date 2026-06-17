package com.empresa.cardtransactionsystem.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("env-aws")
public class AuthSecretsValidationConfig {

    private final String opaqueTokenFromEnv;

    public AuthSecretsValidationConfig(@Value("${AUTH_OPAQUE_TOKEN:}") String opaqueTokenFromEnv) {
        this.opaqueTokenFromEnv = opaqueTokenFromEnv;
    }

    @PostConstruct
    void validate() {
        if (opaqueTokenFromEnv == null || opaqueTokenFromEnv.isBlank()) {
            throw new IllegalStateException(
                    "AUTH_OPAQUE_TOKEN nao configurado: defina um valor seguro via variavel de ambiente antes do deploy em AWS.");
        }
    }
}
