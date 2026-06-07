package com.empresa.cardtransactionsystem.adapters.inbound.logging;

import org.slf4j.Logger;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StructuredLogger {

    private final Logger logger;
    private final String correlationId;

    private StructuredLogger(Logger logger, String correlationId) {
        this.logger = logger;
        this.correlationId = correlationId;
        MDC.put("correlationId", correlationId);
    }

    public static StructuredLogger of(Logger logger) {
        return new StructuredLogger(logger, UUID.randomUUID().toString());
    }

    public static StructuredLogger of(Logger logger, String correlationId) {
        return new StructuredLogger(logger, correlationId);
    }

    public void info(String message, String... fields) {
        MDC.put("messageType", "info");
        logger.info(message, buildContext(fields));
        MDC.remove("messageType");
    }

    public void debug(String message, String... fields) {
        MDC.put("messageType", "debug");
        logger.debug(message, buildContext(fields));
        MDC.remove("messageType");
    }

    public void warn(String message, String... fields) {
        MDC.put("messageType", "warn");
        logger.warn(message, buildContext(fields));
        MDC.remove("messageType");
    }

    public void error(String message, Throwable ex, String... fields) {
        MDC.put("messageType", "error");
        MDC.put("exceptionType", ex.getClass().getSimpleName());
        logger.error(message + " | exception: " + ex.getMessage(), ex, buildContext(fields));
        MDC.remove("messageType");
        MDC.remove("exceptionType");
    }

    public void addContext(String key, Object value) {
        MDC.put(key, String.valueOf(value));
    }

    public void removeContext(String key) {
        MDC.remove(key);
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void clear() {
        MDC.clear();
    }

    private String buildContext(String... fields) {
        if (fields.length == 0) return "";
        Map<String, String> context = new HashMap<>();
        for (int i = 0; i < fields.length; i += 2) {
            if (i + 1 < fields.length) {
                String key = fields[i];
                String value = fields[i + 1];
                context.put(key, value);
                MDC.put(key, value);
            }
        }
        return context.toString();
    }
}
