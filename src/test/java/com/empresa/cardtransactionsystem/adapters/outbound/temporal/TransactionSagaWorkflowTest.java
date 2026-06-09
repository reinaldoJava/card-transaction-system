package com.empresa.cardtransactionsystem.adapters.outbound.temporal;

import com.empresa.cardtransactionsystem.domain.model.Brand;
import com.empresa.cardtransactionsystem.domain.model.CardToken;
import com.empresa.cardtransactionsystem.domain.model.FraudScore;
import com.empresa.cardtransactionsystem.domain.model.SagaPayload;
import com.empresa.cardtransactionsystem.domain.model.TransactionStatus;
import com.empresa.cardtransactionsystem.domain.model.ValidationResult;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.testing.TestWorkflowExtension;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class TransactionSagaWorkflowTest {

    static class TestActivities implements TransactionSagaActivities {
        @Override public void validateTransaction(SagaPayload payload) {}
        @Override public ValidationResult validateBusinessRules(SagaPayload payload) { return null; }
        @Override public FraudScore analyzeFraud(SagaPayload payload) { return null; }
        @Override public void approveTransaction(String transactionId, UUID correlationId, String traceparent) {}
        @Override public void rejectTransaction(String transactionId, UUID correlationId, String reason, String traceparent) {}
    }

    @RegisterExtension
    static final TestWorkflowExtension testWorkflow = TestWorkflowExtension.newBuilder()
            .setWorkflowTypes(TransactionSagaWorkflowImpl.class)
            .setDoNotStart(true)
            .build();

    @Test
    void shouldApproveWhenValidationPassesAndLowFraudScore(
            TestWorkflowEnvironment env, Worker worker) {
        TransactionSagaActivities activities = spy(new TestActivities());
        doReturn(ValidationResult.valid()).when(activities).validateBusinessRules(any());
        doReturn(FraudScore.of(30)).when(activities).analyzeFraud(any());

        worker.registerActivitiesImplementations(activities);
        env.start();

        TransactionSagaWorkflow workflow = env.getWorkflowClient().newWorkflowStub(
                TransactionSagaWorkflow.class,
                WorkflowOptions.newBuilder().setTaskQueue(TransactionSagaWorkflow.TASK_QUEUE).build());
        workflow.execute(buildPayload());

        verify(activities).validateTransaction(any());
        verify(activities).validateBusinessRules(any());
        verify(activities).analyzeFraud(any());
        verify(activities).approveTransaction(any(), any(), any());
        verify(activities, never()).rejectTransaction(any(), any(), any(), any());
    }

    @Test
    void shouldRejectWhenFraudScoreExceedsThreshold(
            TestWorkflowEnvironment env, Worker worker) {
        TransactionSagaActivities activities = spy(new TestActivities());
        doReturn(ValidationResult.valid()).when(activities).validateBusinessRules(any());
        doReturn(FraudScore.of(90)).when(activities).analyzeFraud(any());

        worker.registerActivitiesImplementations(activities);
        env.start();

        TransactionSagaWorkflow workflow = env.getWorkflowClient().newWorkflowStub(
                TransactionSagaWorkflow.class,
                WorkflowOptions.newBuilder().setTaskQueue(TransactionSagaWorkflow.TASK_QUEUE).build());
        workflow.execute(buildPayload());

        verify(activities).rejectTransaction(any(), any(), any(), any());
        verify(activities, never()).approveTransaction(any(), any(), any());
    }

    @Test
    void shouldRejectWhenBusinessRulesInvalid(
            TestWorkflowEnvironment env, Worker worker) {
        TransactionSagaActivities activities = spy(new TestActivities());
        doReturn(ValidationResult.invalid("Insufficient credit")).when(activities).validateBusinessRules(any());

        worker.registerActivitiesImplementations(activities);
        env.start();

        TransactionSagaWorkflow workflow = env.getWorkflowClient().newWorkflowStub(
                TransactionSagaWorkflow.class,
                WorkflowOptions.newBuilder().setTaskQueue(TransactionSagaWorkflow.TASK_QUEUE).build());
        workflow.execute(buildPayload());

        verify(activities).rejectTransaction(any(), any(), any(), any());
        verify(activities, never()).analyzeFraud(any());
        verify(activities, never()).approveTransaction(any(), any(), any());
    }

    private SagaPayload buildPayload() {
        return new SagaPayload(
                "TX-TEMPORAL-001",
                UUID.randomUUID(),
                new CardToken("tok-test"),
                new BigDecimal("300.00"),
                1,
                Brand.VISA,
                TransactionStatus.PENDING,
                LocalDateTime.now(),
                null, null);
    }
}
