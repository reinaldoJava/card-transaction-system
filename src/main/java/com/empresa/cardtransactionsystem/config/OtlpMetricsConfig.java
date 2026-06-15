package com.empresa.cardtransactionsystem.config;

import io.micrometer.core.instrument.Clock;
import io.micrometer.registry.otlp.OtlpConfig;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import java.util.Map;

@Configuration
@Profile("env-local")
public class OtlpMetricsConfig {

    @Bean
    public OtlpMeterRegistry otlpMeterRegistry(
            Environment env,
            Clock clock,
            @Value("${spring.application.name:card-transaction-system}") String appName) {
        OtlpConfig config = new OtlpConfig() {
            @Override
            public String get(String key) {
                return env.getProperty(key);
            }

            @Override
            public Map<String, String> resourceAttributes() {
                return Map.of("service.name", appName);
            }
        };
        return new OtlpMeterRegistry(config, clock);
    }
}
