package com.empresa.cardtransactionsystem.adapters.outbound.bedrock;

import com.empresa.cardtransactionsystem.domain.model.Brand;
import com.empresa.cardtransactionsystem.domain.model.CardToken;
import com.empresa.cardtransactionsystem.domain.model.FraudAnalysisRequest;
import com.empresa.cardtransactionsystem.domain.model.FraudScore;
import com.empresa.cardtransactionsystem.domain.model.MerchantProfile;
import com.empresa.cardtransactionsystem.domain.model.TransactionHistory;
import com.empresa.cardtransactionsystem.domain.ports.output.FraudAnalysisPort;
import com.empresa.cardtransactionsystem.domain.ports.output.TransactionHistoryPort;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Profile("fraud-bedrock")
public class BedrockFraudAnalysisAdapter implements FraudAnalysisPort {

    private static final Pattern SCORE_PATTERN = Pattern.compile("\"fraud_score\"\\s*:\\s*(\\d+)");
    private static final String SYSTEM_PROMPT = """
            You are a fraud detection specialist analyzing credit card transactions.
            Use the available tools to gather client history and merchant profile.
            After gathering information, respond with ONLY this JSON: {"fraud_score": <0-100>}
            0 = no fraud risk, 100 = definite fraud.
            Consider: transaction velocity, amount vs history, merchant risk profile.
            """;

    private final BedrockRuntimeClient bedrockClient;
    private final TransactionHistoryPort historyPort;
    private final ObservationRegistry observationRegistry;
    private final String modelId;

    public BedrockFraudAnalysisAdapter(
            BedrockRuntimeClient bedrockClient,
            TransactionHistoryPort historyPort,
            ObservationRegistry observationRegistry,
            @Value("${bedrock.fraud-agent.model-id:us.anthropic.claude-haiku-4-5-20251001-v1:0}") String modelId) {
        this.bedrockClient = bedrockClient;
        this.historyPort = historyPort;
        this.observationRegistry = observationRegistry;
        this.modelId = modelId;
    }

    @Override
    public FraudScore analyze(FraudAnalysisRequest request) {
        return Observation.createNotStarted("fraud.analyze", observationRegistry)
                .contextualName("bedrock.fraud-analysis")
                .lowCardinalityKeyValue("model_id", modelId)
                .observe(() -> executeAnalysis(request));
    }

    private FraudScore executeAnalysis(FraudAnalysisRequest request) {
        List<Message> messages = new ArrayList<>();
        messages.add(userMessage(buildUserPrompt(request)));

        while (true) {
            ConverseResponse response = bedrockClient.converse(ConverseRequest.builder()
                    .modelId(modelId)
                    .system(SystemContentBlock.builder().text(SYSTEM_PROMPT).build())
                    .messages(messages)
                    .toolConfig(buildToolConfig())
                    .build());

            Message assistantMessage = response.output().message();
            messages.add(assistantMessage);

            if (response.stopReason() == StopReason.TOOL_USE) {
                Message toolResultMessage = executeToolCalls(assistantMessage.content());
                messages.add(toolResultMessage);
            } else {
                String text = assistantMessage.content().stream()
                        .filter(b -> b.text() != null)
                        .map(ContentBlock::text)
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("No text in Bedrock response"));
                return FraudScore.of(extractScore(text));
            }
        }
    }

    private Message executeToolCalls(List<ContentBlock> contentBlocks) {
        List<ContentBlock> toolResults = contentBlocks.stream()
                .filter(b -> b.toolUse() != null)
                .map(b -> executeToolCall(b.toolUse()))
                .toList();

        return Message.builder()
                .role(ConversationRole.USER)
                .content(toolResults)
                .build();
    }

    private ContentBlock executeToolCall(ToolUseBlock toolUse) {
        String result = switch (toolUse.name()) {
            case "get_client_history" -> {
                String cardToken = toolUse.input().asMap().get("card_token").asString();
                TransactionHistory history = historyPort.findByCardToken(new CardToken(cardToken));
                yield "velocity_last_24h=%d, recent_count=%d, total_30d=%s"
                        .formatted(history.velocityLast24h(), history.recent().size(), history.totalAmountLast30Days());
            }
            case "get_merchant_profile" -> {
                String brand = toolUse.input().asMap().get("brand").asString();
                MerchantProfile profile = MerchantProfile.forBrand(Brand.valueOf(brand));
                yield "brand=%s, risk_multiplier=%s".formatted(profile.brand(), profile.riskMultiplier());
            }
            default -> throw new IllegalArgumentException("Unknown tool: " + toolUse.name());
        };

        return ContentBlock.builder()
                .toolResult(ToolResultBlock.builder()
                        .toolUseId(toolUse.toolUseId())
                        .content(ToolResultContentBlock.builder().text(result).build())
                        .build())
                .build();
    }

    private ToolConfiguration buildToolConfig() {
        return ToolConfiguration.builder()
                .tools(
                        tool("get_client_history",
                                "Get client transaction history and velocity for fraud analysis",
                                Document.mapBuilder()
                                        .putString("type", "object")
                                        .putDocument("properties", Document.mapBuilder()
                                                .putDocument("card_token", Document.mapBuilder()
                                                        .putString("type", "string")
                                                        .putString("description", "Tokenized card identifier")
                                                        .build())
                                                .build())
                                        .putList("required", List.of(Document.fromString("card_token")))
                                        .build()),
                        tool("get_merchant_profile",
                                "Get merchant risk profile by brand",
                                Document.mapBuilder()
                                        .putString("type", "object")
                                        .putDocument("properties", Document.mapBuilder()
                                                .putDocument("brand", Document.mapBuilder()
                                                        .putString("type", "string")
                                                        .putString("description", "Card brand: VISA, MASTER, AMEX")
                                                        .build())
                                                .build())
                                        .putList("required", List.of(Document.fromString("brand")))
                                        .build())
                )
                .build();
    }

    private Tool tool(String name, String description, Document schema) {
        return Tool.builder()
                .toolSpec(ToolSpecification.builder()
                        .name(name)
                        .description(description)
                        .inputSchema(ToolInputSchema.builder().json(schema).build())
                        .build())
                .build();
    }

    private Message userMessage(String text) {
        return Message.builder()
                .role(ConversationRole.USER)
                .content(ContentBlock.builder().text(text).build())
                .build();
    }

    private String buildUserPrompt(FraudAnalysisRequest request) {
        return """
                Analyze this transaction for fraud:
                - Card Token: %s
                - Amount: %s
                - Installments: %d
                - Brand: %s
                """.formatted(
                request.cardToken().value(),
                request.amount(),
                request.installments(),
                request.brand().name());
    }

    private int extractScore(String text) {
        Matcher matcher = SCORE_PATTERN.matcher(text);
        if (matcher.find()) {
            int score = Integer.parseInt(matcher.group(1));
            return Math.min(100, Math.max(0, score));
        }
        throw new IllegalStateException("Could not extract fraud_score from: " + text);
    }
}
