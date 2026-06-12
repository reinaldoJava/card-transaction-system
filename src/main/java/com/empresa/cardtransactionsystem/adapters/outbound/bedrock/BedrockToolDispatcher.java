package com.empresa.cardtransactionsystem.adapters.outbound.bedrock;

import com.empresa.cardtransactionsystem.domain.model.Brand;
import com.empresa.cardtransactionsystem.domain.model.CardToken;
import com.empresa.cardtransactionsystem.domain.model.FraudCandidate;
import com.empresa.cardtransactionsystem.domain.model.MerchantProfile;
import com.empresa.cardtransactionsystem.domain.model.TransactionHistory;
import com.empresa.cardtransactionsystem.domain.ports.output.TransactionHistoryPort;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

import java.util.List;

@Component
public class BedrockToolDispatcher {

    private final TransactionHistoryPort historyPort;
    private final FraudContextBuilder contextBuilder;

    public BedrockToolDispatcher(TransactionHistoryPort historyPort, FraudContextBuilder contextBuilder) {
        this.historyPort = historyPort;
        this.contextBuilder = contextBuilder;
    }

    public Message executeToolCalls(List<ContentBlock> contentBlocks, FraudCandidate request) {
        List<ContentBlock> toolResults = contentBlocks.stream()
                .filter(b -> b.toolUse() != null)
                .map(b -> executeToolCall(b.toolUse(), request))
                .toList();

        return Message.builder()
                .role(ConversationRole.USER)
                .content(toolResults)
                .build();
    }

    private ContentBlock executeToolCall(ToolUseBlock toolUse, FraudCandidate request) {
        String result = switch (toolUse.name()) {
            case "get_client_history"   -> contextBuilder.buildHistoryResult(resolveHistory(toolUse), request.amount());
            case "get_merchant_profile" -> buildMerchantResult(toolUse);
            case "compute_risk_signals" -> contextBuilder.buildRiskSignals(request);
            case "get_geo_context"      -> contextBuilder.buildGeoContext(request);
            default -> throw new IllegalArgumentException("Unknown tool: " + toolUse.name());
        };
        return wrapToolResult(toolUse.toolUseId(), result);
    }

    private TransactionHistory resolveHistory(ToolUseBlock toolUse) {
        String cardToken = toolUse.input().asMap().get("card_token").asString();
        return historyPort.findByCardToken(new CardToken(cardToken));
    }

    private String buildMerchantResult(ToolUseBlock toolUse) {
        String brand = toolUse.input().asMap().get("brand").asString();
        MerchantProfile profile = MerchantProfile.forBrand(Brand.valueOf(brand));
        return "brand=%s, cnp_risk=%s, risk_multiplier=%s".formatted(
                profile.brand(),
                profile.brand() == Brand.AMEX ? "HIGH" : "STANDARD",
                profile.riskMultiplier());
    }

    private ContentBlock wrapToolResult(String toolUseId, String result) {
        ToolResultContentBlock content = ToolResultContentBlock.builder()
                .text(result)
                .build();
        ToolResultBlock toolResult = ToolResultBlock.builder()
                .toolUseId(toolUseId)
                .content(content)
                .build();
        return ContentBlock.builder()
                .toolResult(toolResult)
                .build();
    }
}
