package com.empresa.cardtransactionsystem.adapters.outbound.temporal;

import com.empresa.cardtransactionsystem.domain.model.FraudScore;
import com.empresa.cardtransactionsystem.domain.model.SagaPayload;
import com.empresa.cardtransactionsystem.domain.model.ValidationResult;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.util.UUID;

@ActivityInterface
public interface TransactionSagaActivities {

    @ActivityMethod
    void validateTransaction(SagaPayload payload);

    @ActivityMethod
    ValidationResult validateBusinessRules(SagaPayload payload);

    @ActivityMethod
    FraudScore analyzeFraud(SagaPayload payload);

    /** Fallback called when analyzeFraud fails — applies static degraded-mode rules. */
    @ActivityMethod
    FraudScore evaluateFraudFallback(SagaPayload payload);

    @ActivityMethod
    void approveTransaction(String transactionId, UUID correlationId, String traceparent);

    @ActivityMethod
    void rejectTransaction(String transactionId, UUID correlationId, String reason, String traceparent);
}
