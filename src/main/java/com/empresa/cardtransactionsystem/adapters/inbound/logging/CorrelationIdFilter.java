package com.empresa.cardtransactionsystem.adapters.inbound.logging;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
public class CorrelationIdFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(CorrelationIdFilter.class);
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    private final Tracer tracer;

    public CorrelationIdFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String correlationId = httpRequest.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = UUID.randomUUID().toString();
        }

        StructuredLogger sl = StructuredLogger.of(logger, tracer, correlationId);
        sl.addContext("method", httpRequest.getMethod());
        sl.addContext("path", httpRequest.getRequestURI());

        // Dispara o enriquecimento do MDC com os dados de contexto e telemetria
        sl.debug("Incoming request processing started");

        httpResponse.setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            chain.doFilter(request, response);
        } finally {
            sl.clear();
        }
    }

    @Override
    public void destroy() {
    }
}
