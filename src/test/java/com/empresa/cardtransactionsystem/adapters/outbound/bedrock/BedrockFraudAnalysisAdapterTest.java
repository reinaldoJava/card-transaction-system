package com.empresa.cardtransactionsystem.adapters.outbound.bedrock;

import com.empresa.cardtransactionsystem.domain.model.Brand;
import com.empresa.cardtransactionsystem.domain.model.CardToken;
import com.empresa.cardtransactionsystem.domain.model.FraudAnalysisRequest;
import com.empresa.cardtransactionsystem.domain.model.FraudScore;
import com.empresa.cardtransactionsystem.domain.model.TransactionHistory;
import com.empresa.cardtransactionsystem.domain.ports.output.TransactionHistoryPort;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;
import software.amazon.awssdk.core.document.Document;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BedrockFraudAnalysisAdapter")
class BedrockFraudAnalysisAdapterTest {

    @Mock private BedrockRuntimeClient bedrockClient;
    @Mock private TransactionHistoryPort historyPort;

    private BedrockFraudAnalysisAdapter adapter;

    private static final String MODEL_ID = "us.anthropic.claude-haiku-4-5-20251001-v1:0";

    @BeforeEach
    void setUp() {
        adapter = new BedrockFraudAnalysisAdapter(bedrockClient, historyPort, ObservationRegistry.NOOP, MODEL_ID);
        lenient().when(historyPort.findByCardToken(any())).thenReturn(TransactionHistory.empty());
    }

    @Test
    @DisplayName("should return FraudScore from Bedrock direct response (no tool use)")
    void shouldReturnScoreFromDirectResponse() {
        when(bedrockClient.converse(any(ConverseRequest.class))).thenReturn(
                converseResponse(StopReason.END_TURN, """
                        After analyzing the transaction data, the fraud score is:
                        {"fraud_score": 15}
                        """)
        );

        FraudScore score = adapter.analyze(request());

        assertThat(score.score()).isEqualTo(15);
    }

    @Test
    @DisplayName("should handle tool use loop and return score after tool results")
    void shouldHandleToolUseLoopAndReturnScore() {
        ToolUseBlock toolUse = ToolUseBlock.builder()
                .toolUseId("tool-1")
                .name("get_client_history")
                .input(Document.mapBuilder()
                        .putString("card_token", "token-uuid")
                        .build())
                .build();

        when(bedrockClient.converse(any(ConverseRequest.class)))
                .thenReturn(converseResponseWithToolUse(toolUse))
                .thenReturn(converseResponse(StopReason.END_TURN, """
                        Based on the client history, fraud score: {"fraud_score": 42}
                        """));

        FraudScore score = adapter.analyze(request());

        assertThat(score.score()).isEqualTo(42);
    }

    @Test
    @DisplayName("should extract score from JSON embedded in text")
    void shouldExtractScoreFromJsonInText() {
        when(bedrockClient.converse(any(ConverseRequest.class))).thenReturn(
                converseResponse(StopReason.END_TURN, "Low risk transaction. {\"fraud_score\": 5}")
        );

        FraudScore score = adapter.analyze(request());

        assertThat(score.score()).isEqualTo(5);
    }

    private FraudAnalysisRequest request() {
        return new FraudAnalysisRequest(
                new CardToken("token-uuid"),
                new BigDecimal("500.00"), 3, Brand.VISA
        );
    }

    private ConverseResponse converseResponse(StopReason stopReason, String text) {
        return ConverseResponse.builder()
                .stopReason(stopReason)
                .output(ConverseOutput.builder()
                        .message(Message.builder()
                                .content(ContentBlock.builder().text(text).build())
                                .build())
                        .build())
                .build();
    }

    private ConverseResponse converseResponseWithToolUse(ToolUseBlock toolUse) {
        return ConverseResponse.builder()
                .stopReason(StopReason.TOOL_USE)
                .output(ConverseOutput.builder()
                        .message(Message.builder()
                                .content(ContentBlock.builder().toolUse(toolUse).build())
                                .build())
                        .build())
                .build();
    }
}
