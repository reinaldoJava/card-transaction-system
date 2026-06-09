package com.empresa.cardtransactionsystem.adapters.outbound.temporal;

import com.empresa.cardtransactionsystem.domain.model.FraudScore;
import com.empresa.cardtransactionsystem.domain.model.SagaPayload;
import com.empresa.cardtransactionsystem.domain.model.ValidationResult;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
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

        FraudScore fraudScore = activities.analyzeFraud(payload);
        if (fraudScore.exceedsThreshold(fraudThreshold)) {
            activities.rejectTransaction(
                    payload.transactionId(), payload.correlationId(),
                    "High fraud score: " + fraudScore.score(), payload.traceparent());
            return;
        }

        activities.approveTransaction(
                payload.transactionId(), payload.correlationId(), payload.traceparent());
    }
}
