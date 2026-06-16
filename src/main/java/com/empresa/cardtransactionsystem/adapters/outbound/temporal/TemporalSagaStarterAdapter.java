package com.empresa.cardtransactionsystem.adapters.outbound.temporal;

import com.empresa.cardtransactionsystem.domain.model.SagaPayload;
import com.empresa.cardtransactionsystem.domain.ports.output.SagaStarterPort;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("saga-temporal")
public class TemporalSagaStarterAdapter implements SagaStarterPort {

    private final WorkflowClient workflowClient;

    public TemporalSagaStarterAdapter(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    @Override
    public void start(SagaPayload payload) {
        TransactionSagaWorkflow workflow = workflowClient.newWorkflowStub(
                TransactionSagaWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(TransactionSagaWorkflow.TASK_QUEUE)
                        .setWorkflowId(payload.correlationId().toString())
                        .build());
        WorkflowClient.start(workflow::execute, payload);
    }
}
