package com.empresa.cardtransactionsystem.adapters.inbound.logging;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Propaga o X-Correlation-ID (lê do header ou gera) e o devolve no response, e coloca
 * correlationId/method/path no MDC durante a requisição (aparecem em todos os logs da thread).
 *
 * trace_id/span_id NÃO são tratados aqui — o Micrometer Tracing (bridge OTel) já injeta
 * traceId/spanId no MDC por escopo de span, e o logback-spring.xml os mapeia para trace_id/span_id.
 */
@Component
public class CorrelationIdFilter implements Filter {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String correlationId = httpRequest.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        httpResponse.setHeader(CORRELATION_ID_HEADER, correlationId);

        MDC.put("correlationId", correlationId);
        MDC.put("method", httpRequest.getMethod());
        MDC.put("path", httpRequest.getRequestURI());
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("correlationId");
            MDC.remove("method");
            MDC.remove("path");
        }
    }
}
