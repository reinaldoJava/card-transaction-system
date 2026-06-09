package com.empresa.cardtransactionsystem.adapters.outbound.temporal;

import com.empresa.cardtransactionsystem.domain.model.SagaPayload;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface TransactionSagaWorkflow {

    String TASK_QUEUE = "card-transaction-task-queue";

    @WorkflowMethod
    void execute(SagaPayload payload);
}
