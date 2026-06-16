package com.empresa.cardtransactionsystem.adapters.outbound.observability;

import io.micrometer.registry.otlp.OtlpConfig;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import io.micrometer.core.instrument.Clock;

public class FlushableOtlpMeterRegistry extends OtlpMeterRegistry {

    public FlushableOtlpMeterRegistry(OtlpConfig config, Clock clock) {
        super(config, clock);
    }

    public void flush() {
        publish();
    }
}
