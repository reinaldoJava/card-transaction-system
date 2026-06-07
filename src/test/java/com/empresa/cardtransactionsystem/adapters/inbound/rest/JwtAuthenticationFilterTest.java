package com.empresa.cardtransactionsystem.adapters.inbound.rest;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static jakarta.servlet.http.HttpServletResponse.SC_OK;
import static jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("JwtAuthenticationFilter")
class JwtAuthenticationFilterTest {

    private static final String SECRET = "test-secret-key-for-unit-tests-only-32b";
    private SecretKey secretKey;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        secretKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        filter = new JwtAuthenticationFilter(secretKey);
    }

    @Test
    @DisplayName("should allow request with valid Bearer token")
    void shouldAllowValidBearerToken() throws Exception {
        var request = new MockHttpServletRequest("POST", "/process");
        request.addHeader("Authorization", "Bearer " + generateToken());
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(SC_OK);
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("should return 401 when Authorization header is missing")
    void shouldReturn401WhenAuthorizationHeaderMissing() throws Exception {
        var request = new MockHttpServletRequest("POST", "/process");
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(SC_UNAUTHORIZED);
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("should return 401 when token is invalid")
    void shouldReturn401WhenTokenIsInvalid() throws Exception {
        var request = new MockHttpServletRequest("POST", "/process");
        request.addHeader("Authorization", "Bearer invalid.token.here");
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(SC_UNAUTHORIZED);
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("should return 401 when Authorization header is not Bearer scheme")
    void shouldReturn401WhenNotBearerScheme() throws Exception {
        var request = new MockHttpServletRequest("POST", "/process");
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(SC_UNAUTHORIZED);
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("should bypass JWT validation for /auth paths")
    void shouldBypassValidationForAuthPaths() throws Exception {
        var request = new MockHttpServletRequest("POST", "/auth/login");
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(SC_OK);
        verify(chain).doFilter(request, response);
    }

    private String generateToken() {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("test-user")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .signWith(secretKey)
                .compact();
    }
}
