package com.empresa.cardtransactionsystem.adapters.outbound.bedrock;

import com.empresa.cardtransactionsystem.domain.model.Brand;
import com.empresa.cardtransactionsystem.domain.model.CardToken;
import com.empresa.cardtransactionsystem.domain.model.FraudCandidate;
import com.empresa.cardtransactionsystem.domain.model.FraudScore;
import com.empresa.cardtransactionsystem.domain.model.TransactionHistory;
import com.empresa.cardtransactionsystem.domain.ports.output.ClientProfilePort;
import com.empresa.cardtransactionsystem.domain.ports.output.TransactionHistoryPort;
import com.empresa.cardtransactionsystem.domain.service.GeoLocationRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BedrockFraudAnalysisAdapter")
class BedrockFraudAnalysisAdapterTest {

    @Mock private BedrockRuntimeClient bedrockClient;
    @Mock private TransactionHistoryPort historyPort;
    @Mock private ClientProfilePort clientProfilePort;
    @Mock private GeoLocationRegistry geoLocationRegistry;
    @Mock private SsmClient ssmClient;

    private BedrockFraudAnalysisAdapter adapter;

    private static final String MODEL_ID = "us.anthropic.claude-haiku-4-5-20251001-v1:0";
    private static final String SYSTEM_PROMPT_PARAM = "/card-transaction-system/fraud/bedrock/system-prompt";

    @BeforeEach
    void setUp() {
        when(ssmClient.getParameter(any(software.amazon.awssdk.services.ssm.model.GetParameterRequest.class)))
                .thenReturn(software.amazon.awssdk.services.ssm.model.GetParameterResponse.builder()
                        .parameter(software.amazon.awssdk.services.ssm.model.Parameter.builder()
                                .value("You are a fraud analyst. {\"fraud_score\": 0}")
                                .build())
                        .build());

        FraudContextBuilder contextBuilder = new FraudContextBuilder(clientProfilePort, geoLocationRegistry);
        BedrockPromptFactory promptFactory = new BedrockPromptFactory(ssmClient, SYSTEM_PROMPT_PARAM);
        promptFactory.loadSystemPrompt();
        BedrockToolDispatcher toolDispatcher = new BedrockToolDispatcher(historyPort, contextBuilder);
        FraudScoreExtractor scoreExtractor = new FraudScoreExtractor();

        adapter = new BedrockFraudAnalysisAdapter(
                bedrockClient, promptFactory, toolDispatcher, scoreExtractor,
                ObservationRegistry.NOOP,
                io.github.resilience4j.circuitbreaker.CircuitBreaker.ofDefaults("bedrockFraudTest"),
                MODEL_ID);

        lenient().when(historyPort.findByCardToken(any())).thenReturn(TransactionHistory.empty());
        lenient().when(clientProfilePort.findByCardToken(any())).thenReturn(Optional.empty());
        lenient().when(geoLocationRegistry.findByCode(any())).thenReturn(Optional.empty());
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

    private FraudCandidate request() {
        return new FraudCandidate(
                new CardToken("token-uuid"),
                new BigDecimal("500.00"), 3, Brand.VISA, "SAO_PAULO_CENTRO");
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
