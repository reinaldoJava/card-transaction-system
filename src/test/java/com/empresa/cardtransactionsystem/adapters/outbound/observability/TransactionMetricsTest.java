package com.empresa.cardtransactionsystem.adapters.outbound.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TransactionMetrics")
class TransactionMetricsTest {

    private MeterRegistry registry;
    private TransactionMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new TransactionMetrics(registry);
    }

    @Test
    @DisplayName("should increment transactions.submitted counter")
    void shouldIncrementSubmitted() {
        metrics.recordSubmitted();
        metrics.recordSubmitted();

        Counter counter = registry.find("transactions.submitted").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("should increment transactions.completed with outcome=rejected and reason=cache_fraud_score")
    void shouldIncrementCacheRejected() {
        metrics.recordCacheRejected();

        Counter counter = registry.find("transactions.completed")
                .tag("outcome", "rejected")
                .tag("reason", "cache_fraud_score")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("should increment transactions.completed with outcome=approved")
    void shouldIncrementApproved() {
        metrics.recordApproved();

        Counter counter = registry.find("transactions.completed")
                .tag("outcome", "approved")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("should increment transactions.completed with outcome=rejected and reason=fraud_analysis")
    void shouldIncrementFraudRejected() {
        metrics.recordFraudRejected();

        Counter counter = registry.find("transactions.completed")
                .tag("outcome", "rejected")
                .tag("reason", "fraud_analysis")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("should record fraud score in distribution summary")
    void shouldRecordFraudScoreDistribution() {
        metrics.recordFraudScore(30);
        metrics.recordFraudScore(75);
        metrics.recordFraudScore(90);

        DistributionSummary summary = registry.find("fraud.score.distribution").summary();
        assertThat(summary).isNotNull();
        assertThat(summary.count()).isEqualTo(3);
        assertThat(summary.mean()).isEqualTo(65.0);
    }
}
