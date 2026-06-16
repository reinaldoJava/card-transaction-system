package com.empresa.cardtransactionsystem.adapters.outbound.bedrock;

import com.empresa.cardtransactionsystem.domain.model.FraudCandidate;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class BedrockPromptFactory {

    private final SsmClient ssmClient;
    private final String systemPromptParameterName;

    private String systemPrompt;

    public BedrockPromptFactory(
            SsmClient ssmClient,
            @Value("${bedrock.fraud-agent.system-prompt-parameter:/card-transaction-system/fraud/bedrock/system-prompt}")
            String systemPromptParameterName) {
        this.ssmClient = ssmClient;
        this.systemPromptParameterName = systemPromptParameterName;
    }

    @PostConstruct
    void loadSystemPrompt() {
        systemPrompt = ssmClient.getParameter(
                GetParameterRequest.builder()
                        .name(systemPromptParameterName)
                        .withDecryption(false)
                        .build()
        ).parameter().value();
    }

    String getSystemPrompt() {
        return systemPrompt;
    }

    public Message buildUserMessage(FraudCandidate request) {
        return userMessage(buildUserPrompt(request));
    }

    public ToolConfiguration buildToolConfig() {
        return ToolConfiguration.builder()
                .tools(clientHistoryTool(), merchantProfileTool(), riskSignalsTool(), geoContextTool())
                .build();
    }

    private Tool clientHistoryTool() {
        return tool("get_client_history",
                "Retrieve the client's full transaction history: velocity, recency, average amount, rejection rate, and burst patterns. Call this first.",
                Document.mapBuilder()
                        .putString("type", "object")
                        .putDocument("properties", Document.mapBuilder()
                                .putDocument("card_token", Document.mapBuilder()
                                        .putString("type", "string")
                                        .putString("description", "Tokenized card identifier")
                                        .build())
                                .build())
                        .putList("required", List.of(Document.fromString("card_token")))
                        .build());
    }

    private Tool merchantProfileTool() {
        return tool("get_merchant_profile",
                "Retrieve the card brand risk profile including CNP fraud risk level.",
                Document.mapBuilder()
                        .putString("type", "object")
                        .putDocument("properties", Document.mapBuilder()
                                .putDocument("brand", Document.mapBuilder()
                                        .putString("type", "string")
                                        .putString("description", "Card brand: VISA, MASTER, AMEX")
                                        .build())
                                .build())
                        .putList("required", List.of(Document.fromString("brand")))
                        .build());
    }

    private Tool riskSignalsTool() {
        return tool("compute_risk_signals",
                "Compute contextual risk signals: time-of-day risk, weekend flag, amount bracket, round-number flag, installment risk. No parameters needed.",
                Document.mapBuilder()
                        .putString("type", "object")
                        .putDocument("properties", Document.mapBuilder().build())
                        .putList("required", List.of())
                        .build());
    }

    private Tool geoContextTool() {
        return tool("get_geo_context",
                "Get the geolocation context: where the client lives (home) vs where this transaction is happening. Use this to assess geographic plausibility. No parameters needed.",
                Document.mapBuilder()
                        .putString("type", "object")
                        .putDocument("properties", Document.mapBuilder().build())
                        .putList("required", List.of())
                        .build());
    }

    private String buildUserPrompt(FraudCandidate request) {
        return """
                Analyze this transaction for fraud and produce a fraud_score.

                TRANSACTION:
                  card_token:   %s
                  amount:       R$%s
                  installments: %d
                  brand:        %s
                  submitted_at: %s

                Use all four tools (get_client_history, get_merchant_profile, compute_risk_signals, get_geo_context) \
                before scoring. Apply the rubric from the system prompt precisely.
                """.formatted(
                request.cardToken().value(),
                request.amount(),
                request.installments(),
                request.brand().name(),
                LocalDateTime.now());
    }

    private Message userMessage(String text) {
        return Message.builder()
                .role(ConversationRole.USER)
                .content(ContentBlock.builder().text(text).build())
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
}
