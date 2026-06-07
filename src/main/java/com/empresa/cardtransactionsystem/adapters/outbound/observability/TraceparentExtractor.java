package com.empresa.cardtransactionsystem.adapters.outbound.observability;

import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class TraceparentExtractor {

    private static final W3CTraceContextPropagator PROPAGATOR = W3CTraceContextPropagator.getInstance();

    private static final TextMapGetter<Map<String, String>> MAP_GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            return carrier != null ? carrier.get(key) : null;
        }
    };

    public String extract() {
        Map<String, String> carrier = new HashMap<>();
        PROPAGATOR.inject(Context.current(), carrier, Map::put);
        return carrier.get("traceparent");
    }

    public Scope restore(String traceparent) {
        if (traceparent == null) {
            return Context.current().makeCurrent();
        }
        Map<String, String> carrier = Map.of("traceparent", traceparent);
        Context parentContext = PROPAGATOR.extract(Context.root(), carrier, MAP_GETTER);
        return parentContext.makeCurrent();
    }
}
