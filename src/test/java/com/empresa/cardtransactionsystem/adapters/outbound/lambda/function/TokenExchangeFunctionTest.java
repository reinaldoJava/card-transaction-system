package com.empresa.cardtransactionsystem.adapters.outbound.lambda.function;

import com.empresa.cardtransactionsystem.adapters.outbound.lambda.JwtGenerator;
import com.empresa.cardtransactionsystem.adapters.outbound.lambda.dto.TokenExchangeRequest;
import com.empresa.cardtransactionsystem.adapters.outbound.lambda.dto.TokenExchangeResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TokenExchangeFunction")
class TokenExchangeFunctionTest {

    private static final String SECRET = "test-secret-key-exactly-32-bytes!!";

    private Function<TokenExchangeRequest, TokenExchangeResponse> function;
    private SecretKey verificationKey;

    @BeforeEach
    void setUp() {
        SecretKey secretKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        JwtGenerator jwtGenerator = new JwtGenerator(secretKey, 24L);
        function = TokenExchangeFunction.create(jwtGenerator);
        verificationKey = secretKey;
    }

    @Test
    @DisplayName("should generate JWT with valid three-part structure")
    void shouldGenerateJwtWithValidStructure() {
        TokenExchangeResponse response = function.apply(new TokenExchangeRequest("opaque-token", "john"));

        assertThat(response.jwt().split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("should include username as subject in JWT payload")
    void shouldIncludeUsernameAsSubject() {
        TokenExchangeResponse response = function.apply(new TokenExchangeRequest("opaque-token", "john"));

        Claims claims = parseClaims(response.jwt());
        assertThat(claims.getSubject()).isEqualTo("john");
    }

    @Test
    @DisplayName("should set expiration date in the future")
    void shouldSetExpirationInFuture() {
        TokenExchangeResponse response = function.apply(new TokenExchangeRequest("opaque-token", "john"));

        Claims claims = parseClaims(response.jwt());
        assertThat(claims.getExpiration()).isAfter(new Date());
    }

    @Test
    @DisplayName("should generate different JWT for different usernames")
    void shouldGenerateDifferentJwtForDifferentUsernames() {
        TokenExchangeResponse john = function.apply(new TokenExchangeRequest("opaque", "john"));
        TokenExchangeResponse jane = function.apply(new TokenExchangeRequest("opaque", "jane"));

        assertThat(john.jwt()).isNotEqualTo(jane.jwt());
    }

    private Claims parseClaims(String jwt) {
        return Jwts.parser()
                .verifyWith(verificationKey)
                .build()
                .parseSignedClaims(jwt)
                .getPayload();
    }
}
