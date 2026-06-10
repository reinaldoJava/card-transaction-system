package com.empresa.cardtransactionsystem.adapters.outbound.temporal;

import com.empresa.cardtransactionsystem.domain.model.FraudScore;
import com.empresa.cardtransactionsystem.domain.model.SagaPayload;
import com.empresa.cardtransactionsystem.domain.model.ValidationResult;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.workflow.Workflow;

import java.time.Duration;

public class TransactionSagaWorkflowImpl implements TransactionSagaWorkflow {

    private final int fraudThreshold;

    private final TransactionSagaActivities activities = Workflow.newActivityStub(
            TransactionSagaActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(30))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(3)
                            .setDoNotRetry(IllegalArgumentException.class.getName())
                            .build())
                    .build());

    public TransactionSagaWorkflowImpl() {
        this.fraudThreshold = 80;
    }

    public TransactionSagaWorkflowImpl(int fraudThreshold) {
        this.fraudThreshold = fraudThreshold;
    }

    @Override
    public void execute(SagaPayload payload) {
        activities.validateTransaction(payload);

        ValidationResult businessRules = activities.validateBusinessRules(payload);
        if (!businessRules.approved()) {
            activities.rejectTransaction(
                    payload.transactionId(), payload.correlationId(),
                    businessRules.reason(), payload.traceparent());
            return;
        }

        FraudScore fraudScore;
        boolean usedFallback = false;
        try {
            fraudScore = activities.analyzeFraud(payload);
        } catch (ActivityFailure e) {
            Workflow.getLogger(TransactionSagaWorkflowImpl.class)
                    .warn("Fraud analysis failed — applying degraded-mode fallback rules: {}", e.getMessage());
            fraudScore = activities.evaluateFraudFallback(payload);
            usedFallback = true;
        }

        if (fraudScore.exceedsThreshold(fraudThreshold)) {
            String reason = usedFallback
                    ? "Fraud analysis unavailable — transaction blocked by fallback policy"
                    : "High fraud score: " + fraudScore.score();
            activities.rejectTransaction(
                    payload.transactionId(), payload.correlationId(),
                    reason, payload.traceparent());
            return;
        }

        activities.approveTransaction(
                payload.transactionId(), payload.correlationId(), payload.traceparent());
    }
}
