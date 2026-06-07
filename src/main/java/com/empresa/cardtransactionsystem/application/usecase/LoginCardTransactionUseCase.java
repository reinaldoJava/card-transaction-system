package com.empresa.cardtransactionsystem.application.usecase;

import com.empresa.cardtransactionsystem.domain.exception.UnauthorizedException;
import com.empresa.cardtransactionsystem.domain.model.auth.JwtToken;
import com.empresa.cardtransactionsystem.domain.model.auth.OpaqueToken;
import com.empresa.cardtransactionsystem.domain.ports.input.LoginUseCase;
import com.empresa.cardtransactionsystem.domain.ports.output.TokenExchangePort;
import com.empresa.cardtransactionsystem.domain.ports.output.UserRepositoryPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class LoginCardTransactionUseCase implements LoginUseCase {

    private final UserRepositoryPort userRepository;
    private final TokenExchangePort tokenExchangePort;
    private final BCryptPasswordEncoder passwordEncoder;
    private final String hardcodedOpaqueToken;

    public LoginCardTransactionUseCase(
            UserRepositoryPort userRepository,
            TokenExchangePort tokenExchangePort,
            BCryptPasswordEncoder passwordEncoder,
            @Value("${auth.hardcoded-opaque-token}") String hardcodedOpaqueToken) {
        this.userRepository = userRepository;
        this.tokenExchangePort = tokenExchangePort;
        this.passwordEncoder = passwordEncoder;
        this.hardcodedOpaqueToken = hardcodedOpaqueToken;
    }

    @Override
    public JwtToken login(String username, String rawPassword) {
        var user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        if (!passwordEncoder.matches(rawPassword, user.hashedPassword())) {
            throw new UnauthorizedException("Invalid credentials");
        }

        return tokenExchangePort.exchange(new OpaqueToken(hardcodedOpaqueToken), username);
    }
}
