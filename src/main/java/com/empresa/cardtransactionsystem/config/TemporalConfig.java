package com.empresa.cardtransactionsystem.config;

import com.empresa.cardtransactionsystem.adapters.outbound.temporal.TransactionSagaActivitiesImpl;
import com.empresa.cardtransactionsystem.adapters.outbound.temporal.TransactionSagaWorkflow;
import com.empresa.cardtransactionsystem.adapters.outbound.temporal.TransactionSagaWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("saga-temporal")
public class TemporalConfig {

    @Bean
    public WorkflowServiceStubs workflowServiceStubs(
            @Value("${temporal.host:localhost}") String host,
            @Value("${temporal.port:7233}") int port) {
        return WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder()
                        .setTarget(host + ":" + port)
                        .build());
    }

    @Bean
    public WorkflowClient workflowClient(WorkflowServiceStubs stubs) {
        return WorkflowClient.newInstance(stubs);
    }

    @Bean(destroyMethod = "shutdown")
    public WorkerFactory workerFactory(
            WorkflowClient client,
            TransactionSagaActivitiesImpl activities,
            @Value("${fraud.agent.threshold:80}") int fraudThreshold) {
        WorkerFactory factory = WorkerFactory.newInstance(client);
        Worker worker = factory.newWorker(TransactionSagaWorkflow.TASK_QUEUE);
        worker.addWorkflowImplementationFactory(
                TransactionSagaWorkflow.class,
                () -> new TransactionSagaWorkflowImpl(fraudThreshold));
        worker.registerActivitiesImplementations(activities);
        factory.start();
        return factory;
    }
}
