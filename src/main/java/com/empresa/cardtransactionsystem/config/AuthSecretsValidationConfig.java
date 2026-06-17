package com.empresa.cardtransactionsystem.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("env-aws")
public class AuthSecretsValidationConfig {

    private static final String INSECURE_DEFAULT_OPAQUE_TOKEN = "550e8400-e29b-41d4-a716-446655440000";

    private final String opaqueToken;

    public AuthSecretsValidationConfig(@Value("${auth.hardcoded-opaque-token}") String opaqueToken) {
        this.opaqueToken = opaqueToken;
    }

    @PostConstruct
    void validate() {
        if (opaqueToken == null || opaqueToken.isBlank() || INSECURE_DEFAULT_OPAQUE_TOKEN.equals(opaqueToken)) {
            throw new IllegalStateException(
                    "AUTH_OPAQUE_TOKEN nao configurado: defina um valor seguro via variavel de ambiente antes do deploy em AWS.");
        }
    }
}
