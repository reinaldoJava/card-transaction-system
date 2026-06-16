package com.empresa.cardtransactionsystem.config;

import com.empresa.cardtransactionsystem.adapters.outbound.observability.FlushableOtlpMeterRegistry;
import io.micrometer.core.instrument.Clock;
import io.micrometer.registry.otlp.OtlpConfig;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;

@Configuration
@Profile("env-aws")
public class ProdObservabilityConfig {

    @Bean
    public SpanExporter otlpNewRelicExporter(
            SsmClient ssmClient,
            @Value("${NR_LICENSE_KEY_SSM_PATH:/card-transaction-system/nr-license-key}") String ssmPath,
            @Value("${OTEL_EXPORTER_OTLP_ENDPOINT:https://otlp.nr-data.net}") String endpoint) {
        String apiKey = fetchLicenseKey(ssmClient, ssmPath);
        return OtlpHttpSpanExporter.builder()
                .setEndpoint(endpoint + "/v1/traces")
                .addHeader("api-key", apiKey)
                .build();
    }

    @Bean
    public OtlpConfig otlpMeterConfig(
            SsmClient ssmClient,
            @Value("${NR_LICENSE_KEY_SSM_PATH:/card-transaction-system/nr-license-key}") String ssmPath,
            @Value("${OTEL_EXPORTER_OTLP_ENDPOINT:https://otlp.nr-data.net}") String endpoint) {
        String apiKey = fetchLicenseKey(ssmClient, ssmPath);
        return key -> switch (key) {
            case "otlp.url" -> endpoint + "/v1/metrics";
            case "otlp.headers" -> "api-key=" + apiKey;
            case "otlp.step" -> "10s";
            default -> null;
        };
    }

    @Bean
    @Primary
    public FlushableOtlpMeterRegistry otlpMeterRegistry(OtlpConfig otlpMeterConfig) {
        return new FlushableOtlpMeterRegistry(otlpMeterConfig, Clock.SYSTEM);
    }

    private String fetchLicenseKey(SsmClient ssmClient, String ssmPath) {
        return ssmClient.getParameter(
                GetParameterRequest.builder().name(ssmPath).withDecryption(true).build()
        ).parameter().value();
    }
}
