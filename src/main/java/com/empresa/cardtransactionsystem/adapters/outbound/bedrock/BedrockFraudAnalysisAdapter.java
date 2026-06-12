package com.empresa.cardtransactionsystem.adapters.outbound.bedrock;

import com.empresa.cardtransactionsystem.domain.model.FraudCandidate;
import com.empresa.cardtransactionsystem.domain.model.FraudScore;
import com.empresa.cardtransactionsystem.domain.ports.output.FraudAnalysisPort;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
@Profile("fraud-bedrock")
public class BedrockFraudAnalysisAdapter implements FraudAnalysisPort {

    private static final int MAX_TOOL_ROUNDS = 10;

    private static final Logger log = LoggerFactory.getLogger(BedrockFraudAnalysisAdapter.class);

    private final BedrockRuntimeClient bedrockClient;
    private final BedrockPromptFactory promptFactory;
    private final BedrockToolDispatcher toolDispatcher;
    private final FraudScoreExtractor scoreExtractor;
    private final ObservationRegistry observationRegistry;
    private final CircuitBreaker circuitBreaker;
    private final String modelId;

    public BedrockFraudAnalysisAdapter(
            BedrockRuntimeClient bedrockClient,
            BedrockPromptFactory promptFactory,
            BedrockToolDispatcher toolDispatcher,
            FraudScoreExtractor scoreExtractor,
            ObservationRegistry observationRegistry,
            @Qualifier("bedrockFraudCircuitBreaker") CircuitBreaker circuitBreaker,
            @Value("${bedrock.fraud-agent.model-id:us.anthropic.claude-haiku-4-5-20251001-v1:0}") String modelId) {
        this.bedrockClient = bedrockClient;
        this.promptFactory = promptFactory;
        this.toolDispatcher = toolDispatcher;
        this.scoreExtractor = scoreExtractor;
        this.observationRegistry = observationRegistry;
        this.circuitBreaker = circuitBreaker;
        this.modelId = modelId;
    }

    @Override
    public FraudScore analyze(FraudCandidate request) {
        return Observation.createNotStarted("fraud.analyze", observationRegistry)
                .contextualName("bedrock.fraud-analysis")
                .lowCardinalityKeyValue("model_id", modelId)
                .observe(() -> callWithBreaker(request));
    }

    private FraudScore callWithBreaker(FraudCandidate request) {
        try {
            return circuitBreaker.executeSupplier(() -> executeAnalysis(request));
        } catch (Exception e) {
            log.warn("Bedrock indisponivel ({}): propagando para a saga.", e.getMessage());
            throw e;
        }
    }

    private FraudScore executeAnalysis(FraudCandidate request) {
        List<Message> messages = new ArrayList<>();
        messages.add(promptFactory.buildUserMessage(request));

        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            ConverseResponse response = callBedrock(messages);
            Message assistantMessage = response.output().message();
            messages.add(assistantMessage);

            if (response.stopReason() != StopReason.TOOL_USE) {
                return extractFraudScore(assistantMessage);
            }
            messages.add(toolDispatcher.executeToolCalls(assistantMessage.content(), request));
        }
        throw new IllegalStateException("Bedrock agent exceeded maximum tool rounds: " + MAX_TOOL_ROUNDS);
    }

    private ConverseResponse callBedrock(List<Message> messages) {
        return bedrockClient.converse(ConverseRequest.builder()
                .modelId(modelId)
                .system(SystemContentBlock.builder().text(promptFactory.getSystemPrompt()).build())
                .messages(messages)
                .toolConfig(promptFactory.buildToolConfig())
                .build());
    }

    private FraudScore extractFraudScore(Message assistantMessage) {
        String text = assistantMessage.content().stream()
                .map(ContentBlock::text)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No text in Bedrock response"));
        return FraudScore.of(scoreExtractor.extract(text));
    }
}