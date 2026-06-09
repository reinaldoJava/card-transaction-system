package com.empresa.cardtransactionsystem.adapters.outbound.saga;

import com.empresa.cardtransactionsystem.domain.model.SagaPayload;
import com.empresa.cardtransactionsystem.domain.ports.output.SagaStarterPort;
import io.micrometer.observation.annotation.Observed;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.StartExecutionRequest;
import tools.jackson.databind.ObjectMapper;

@Component
@Profile("saga-stepfunctions")
public class StepFunctionsSagaStarterAdapter implements SagaStarterPort {

    private final SfnClient stepFunctionsClient;
    private final ObjectMapper objectMapper;
    private final String stateMachineArn;

    public StepFunctionsSagaStarterAdapter(
            SfnClient stepFunctionsClient,
            ObjectMapper objectMapper,
            @Value("${step-functions.state-machine-arn}") String stateMachineArn) {
        this.stepFunctionsClient = stepFunctionsClient;
        this.objectMapper = objectMapper;
        this.stateMachineArn = stateMachineArn;
    }

    @Override
    @Observed(name = "saga.start", contextualName = "sfn.start-execution")
    public void start(SagaPayload payload) {
        try {
            String input = objectMapper.writeValueAsString(payload);
            stepFunctionsClient.startExecution(StartExecutionRequest.builder()
                    .stateMachineArn(stateMachineArn)
                    .input(input)
                    .name(payload.correlationId().toString())
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to start Step Functions execution", e);
        }
    }
}
