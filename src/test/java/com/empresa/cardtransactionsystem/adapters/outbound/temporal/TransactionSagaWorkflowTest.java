package com.empresa.cardtransactionsystem.adapters.outbound.temporal;

import com.empresa.cardtransactionsystem.domain.model.FraudScore;
import com.empresa.cardtransactionsystem.domain.model.SagaPayload;
import com.empresa.cardtransactionsystem.domain.model.ValidationResult;
import com.empresa.cardtransactionsystem.fixture.SagaPayloadFixture;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@Tag("integration")
class TransactionSagaWorkflowTest {

    static class TestActivities implements TransactionSagaActivities {
        @Override public void validateTransaction(SagaPayload payload) {}
        @Override public ValidationResult validateBusinessRules(SagaPayload payload) { return null; }
        @Override public FraudScore analyzeFraud(SagaPayload payload) { return null; }
        @Override public FraudScore evaluateFraudFallback(SagaPayload payload) { return FraudScore.of(0); }
        @Override public void approveTransaction(String transactionId, UUID correlationId, String traceparent) {}
        @Override public void rejectTransaction(String transactionId, UUID correlationId, String reason, String traceparent) {}
    }

    private TestWorkflowEnvironment env;
    private Worker worker;
    private WorkflowClient client;

    @BeforeEach
    void setUp() {
        env = TestWorkflowEnvironment.newInstance();
        worker = env.newWorker(TransactionSagaWorkflow.TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(TransactionSagaWorkflowImpl.class);
        client = env.getWorkflowClient();
    }

    @AfterEach
    void tearDown() {
        if (env != null) {
            env.close();
        }
    }

    @Test
    void shouldApproveWhenValidationPassesAndLowFraudScore() {
        TransactionSagaActivities activities = spy(new TestActivities());
        doReturn(ValidationResult.valid()).when(activities).validateBusinessRules(any());
        doReturn(FraudScore.of(30)).when(activities).analyzeFraud(any());

        worker.registerActivitiesImplementations(activities);
        env.start();

        TransactionSagaWorkflow workflow = client.newWorkflowStub(
                TransactionSagaWorkflow.class,
                WorkflowOptions.newBuilder().setTaskQueue(TransactionSagaWorkflow.TASK_QUEUE).build());
        workflow.execute(SagaPayloadFixture.minimal());

        verify(activities).validateTransaction(any());
        verify(activities).validateBusinessRules(any());
        verify(activities).analyzeFraud(any());
        verify(activities).approveTransaction(any(), any(), any());
        verify(activities, never()).rejectTransaction(any(), any(), any(), any());
    }

    @Test
    void shouldRejectWhenFraudScoreExceedsThreshold() {
        TransactionSagaActivities activities = spy(new TestActivities());
        doReturn(ValidationResult.valid()).when(activities).validateBusinessRules(any());
        doReturn(FraudScore.of(90)).when(activities).analyzeFraud(any());

        worker.registerActivitiesImplementations(activities);
        env.start();

        TransactionSagaWorkflow workflow = client.newWorkflowStub(
                TransactionSagaWorkflow.class,
                WorkflowOptions.newBuilder().setTaskQueue(TransactionSagaWorkflow.TASK_QUEUE).build());
        workflow.execute(SagaPayloadFixture.minimal());

        verify(activities).rejectTransaction(any(), any(), any(), any());
        verify(activities, never()).approveTransaction(any(), any(), any());
    }

    @Test
    void shouldRejectWhenBusinessRulesInvalid() {
        TransactionSagaActivities activities = spy(new TestActivities());
        doReturn(ValidationResult.invalid("Insufficient credit")).when(activities).validateBusinessRules(any());

        worker.registerActivitiesImplementations(activities);
        env.start();

        TransactionSagaWorkflow workflow = client.newWorkflowStub(
                TransactionSagaWorkflow.class,
                WorkflowOptions.newBuilder().setTaskQueue(TransactionSagaWorkflow.TASK_QUEUE).build());
        workflow.execute(SagaPayloadFixture.minimal());

        verify(activities).rejectTransaction(any(), any(), any(), any());
        verify(activities, never()).analyzeFraud(any());
        verify(activities, never()).approveTransaction(any(), any(), any());
    }
}
