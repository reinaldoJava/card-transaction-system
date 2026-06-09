package com.empresa.cardtransactionsystem.adapters.inbound.logging;

import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;

public class StructuredLogger {

    private final Logger logger;
    private final Tracer tracer;
    private final Map<String, Object> context = new HashMap<>();

    private StructuredLogger(Logger logger, Tracer tracer, String correlationId) {
        this.logger = logger;
        this.tracer = tracer;
        if (correlationId != null) {
            context.put("correlationId", correlationId);
        }
    }

    public static StructuredLogger of(Logger logger, Tracer tracer, String correlationId) {
        return new StructuredLogger(logger, tracer, correlationId);
    }

    public void debug(String message, String... keyValues) {
        try {
            injectMdc(keyValues);
            logger.debug(message);
        } finally {
            removeTransientKeys(keyValues);
        }
    }

    public void info(String message, String... keyValues) {
        try {
            injectMdc(keyValues);
            logger.info(message);
        } finally {
            removeTransientKeys(keyValues);
        }
    }

    public void warn(String message, String... keyValues) {
        try {
            injectMdc(keyValues);
            logger.warn(message);
        } finally {
            removeTransientKeys(keyValues);
        }
    }

    public void error(String message, String... keyValues) {
        try {
            injectMdc(keyValues);
            logger.error(message);
        } finally {
            removeTransientKeys(keyValues);
        }
    }

    public void addContext(String key, String value) {
        context.put(key, value);
    }

    public void clear() {
        context.forEach((k, v) -> MDC.remove(k));
        MDC.remove("trace_id");
        MDC.remove("span_id");
        context.clear();
    }

    private void injectMdc(String... keyValues) {
        context.forEach((k, v) -> MDC.put(k, String.valueOf(v)));

        var span = tracer.currentSpan();
        if (span != null) {
            var ctx = span.context();
            MDC.put("trace_id", ctx.traceId());
            MDC.put("span_id", ctx.spanId());
        }

        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            MDC.put(keyValues[i], keyValues[i + 1]);
        }
    }

    private void removeTransientKeys(String... keyValues) {
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            MDC.remove(keyValues[i]);
        }
    }
}
