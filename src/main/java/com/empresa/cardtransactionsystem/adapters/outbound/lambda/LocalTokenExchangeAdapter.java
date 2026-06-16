package com.empresa.cardtransactionsystem.adapters.outbound.lambda;

import com.empresa.cardtransactionsystem.domain.model.auth.JwtToken;
import com.empresa.cardtransactionsystem.domain.model.auth.OpaqueToken;
import com.empresa.cardtransactionsystem.domain.ports.output.TokenExchangePort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * TokenExchangePort for local development (profile env-local).
 * Generates the JWT directly via JwtGenerator instead of invoking a Lambda function.
 */
@Component
@Profile("env-local")
public class LocalTokenExchangeAdapter implements TokenExchangePort {

    private final JwtGenerator jwtGenerator;

    public LocalTokenExchangeAdapter(JwtGenerator jwtGenerator) {
        this.jwtGenerator = jwtGenerator;
    }

    @Override
    public JwtToken exchange(OpaqueToken opaqueToken, String username) {
        return new JwtToken(jwtGenerator.generate(username));
    }
}
