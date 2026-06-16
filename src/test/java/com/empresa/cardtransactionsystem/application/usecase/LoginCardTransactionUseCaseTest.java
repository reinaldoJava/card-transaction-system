package com.empresa.cardtransactionsystem.application.usecase;

import com.empresa.cardtransactionsystem.domain.exception.UnauthorizedException;
import com.empresa.cardtransactionsystem.domain.model.auth.JwtToken;
import com.empresa.cardtransactionsystem.domain.model.auth.OpaqueToken;
import com.empresa.cardtransactionsystem.domain.model.auth.User;
import com.empresa.cardtransactionsystem.domain.ports.output.TokenExchangePort;
import com.empresa.cardtransactionsystem.domain.ports.output.UserRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("LoginCardTransactionUseCase")
class LoginCardTransactionUseCaseTest {

    private static final String OPAQUE_TOKEN = "550e8400-e29b-41d4-a716-446655440000";
    private static final String FAKE_JWT = "eyJ.payload.sig";

    private UserRepositoryPort userRepository;
    private TokenExchangePort tokenExchangePort;
    private BCryptPasswordEncoder passwordEncoder;
    private LoginCardTransactionUseCase useCase;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepositoryPort.class);
        tokenExchangePort = mock(TokenExchangePort.class);
        passwordEncoder = mock(BCryptPasswordEncoder.class);
        useCase = new LoginCardTransactionUseCase(
                userRepository, tokenExchangePort, passwordEncoder, OPAQUE_TOKEN);
    }

    @Test
    @DisplayName("should return JWT when credentials are valid")
    void shouldReturnJwtWhenCredentialsAreValid() {
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(new User("john", "hashed")));
        when(passwordEncoder.matches("secret", "hashed")).thenReturn(true);
        when(tokenExchangePort.exchange(any(OpaqueToken.class), anyString())).thenReturn(new JwtToken(FAKE_JWT));

        assertThat(useCase.login("john", "secret").value()).isEqualTo(FAKE_JWT);
    }

    @Test
    @DisplayName("should call token exchange with hardcoded opaque token")
    void shouldCallTokenExchangeWithHardcodedOpaqueToken() {
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(new User("john", "hashed")));
        when(passwordEncoder.matches("secret", "hashed")).thenReturn(true);
        when(tokenExchangePort.exchange(any(), anyString())).thenReturn(new JwtToken(FAKE_JWT));

        useCase.login("john", "secret");

        verify(tokenExchangePort).exchange(new OpaqueToken(OPAQUE_TOKEN), "john");
    }

    @Test
    @DisplayName("should throw UnauthorizedException when user not found")
    void shouldThrowWhenUserNotFound() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.login("unknown", "secret"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("should throw UnauthorizedException when password does not match")
    void shouldThrowWhenPasswordDoesNotMatch() {
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(new User("john", "hashed")));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);
        assertThatThrownBy(() -> useCase.login("john", "wrong"))
                .isInstanceOf(UnauthorizedException.class);
    }
}
