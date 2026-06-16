package com.empresa.cardtransactionsystem.adapters.outbound.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class TransactionMetrics {

    private final Counter submittedCounter;
    private final Counter cacheRejectedCounter;
    private final Counter approvedCounter;
    private final Counter fraudRejectedCounter;
    private final DistributionSummary fraudScoreSummary;

    public TransactionMetrics(MeterRegistry registry) {
        this.submittedCounter = Counter.builder("transactions.submitted")
                .description("Transactions sent to saga")
                .register(registry);
        this.cacheRejectedCounter = Counter.builder("transactions.completed")
                .tag("outcome", "rejected")
                .tag("reason", "cache_fraud_score")
                .description("Transactions fast-rejected by cached fraud score")
                .register(registry);
        this.approvedCounter = Counter.builder("transactions.completed")
                .tag("outcome", "approved")
                .tag("reason", "fraud_analysis")
                .description("Transactions approved after fraud analysis")
                .register(registry);
        this.fraudRejectedCounter = Counter.builder("transactions.completed")
                .tag("outcome", "rejected")
                .tag("reason", "fraud_analysis")
                .description("Transactions rejected after fraud analysis")
                .register(registry);
        this.fraudScoreSummary = DistributionSummary.builder("fraud.score.distribution")
                .description("Distribution of fraud scores returned by Bedrock")
                .baseUnit("score")
                .register(registry);
    }

    public void recordSubmitted() {
        submittedCounter.increment();
    }

    public void recordCacheRejected() {
        cacheRejectedCounter.increment();
    }

    public void recordApproved() {
        approvedCounter.increment();
    }

    public void recordFraudRejected() {
        fraudRejectedCounter.increment();
    }

    public void recordFraudScore(int score) {
        fraudScoreSummary.record(score);
    }
}
