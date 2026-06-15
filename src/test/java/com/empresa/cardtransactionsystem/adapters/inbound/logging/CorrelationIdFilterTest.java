package com.empresa.cardtransactionsystem.adapters.inbound.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("CorrelationIdFilter")
class CorrelationIdFilterTest {

    private CorrelationIdFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
        filterChain = mock(FilterChain.class);
    }

    @Test
    @DisplayName("should generate correlationId when header is not present")
    void shouldGenerateCorrelationIdWhenHeaderNotPresent() throws IOException, ServletException {
        var request = new MockHttpServletRequest("GET", "/api/transactions");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        String correlationId = response.getHeader("X-Correlation-ID");
        assertThat(correlationId).isNotNull().isNotBlank();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("should use correlationId from header when present")
    void shouldUseCorrelationIdFromHeader() throws IOException, ServletException {
        String providedId = UUID.randomUUID().toString();
        var request = new MockHttpServletRequest("POST", "/api/transactions");
        request.addHeader("X-Correlation-ID", providedId);
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getHeader("X-Correlation-ID")).isEqualTo(providedId);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("should include method in response header")
    void shouldIncludeMethodInResponseHeader() throws IOException, ServletException {
        var request = new MockHttpServletRequest("DELETE", "/api/transactions/123");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getHeader("X-Correlation-ID")).isNotNull();
        verify(filterChain).doFilter(request, response);
    }
}
