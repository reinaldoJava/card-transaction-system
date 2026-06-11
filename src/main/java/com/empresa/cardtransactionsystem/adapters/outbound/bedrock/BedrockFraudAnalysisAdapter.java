package com.empresa.cardtransactionsystem.adapters.outbound.bedrock;

import com.empresa.cardtransactionsystem.domain.model.Brand;
import com.empresa.cardtransactionsystem.domain.model.CardToken;
import com.empresa.cardtransactionsystem.domain.model.ClientProfile;
import com.empresa.cardtransactionsystem.domain.model.FraudAnalysisRequest;
import com.empresa.cardtransactionsystem.domain.model.FraudScore;
import com.empresa.cardtransactionsystem.domain.model.GeoLocation;
import com.empresa.cardtransactionsystem.domain.model.MerchantProfile;
import com.empresa.cardtransactionsystem.domain.model.TransactionHistory;
import com.empresa.cardtransactionsystem.domain.model.TransactionStatus;
import com.empresa.cardtransactionsystem.domain.model.TransactionSummary;
import com.empresa.cardtransactionsystem.domain.ports.output.ClientProfilePort;
import com.empresa.cardtransactionsystem.domain.ports.output.FraudAnalysisPort;
import com.empresa.cardtransactionsystem.domain.ports.output.TransactionHistoryPort;
import com.empresa.cardtransactionsystem.domain.service.GeoLocationRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Profile("fraud-bedrock")
public class BedrockFraudAnalysisAdapter implements FraudAnalysisPort {

    private static final Pattern SCORE_PATTERN = Pattern.compile("\"fraud_score\"\\s*:\\s*(\\d+)");

    private static final String SYSTEM_PROMPT = """
            You are a senior fraud analyst at a Brazilian payment processor. Your task is to \
            score credit card transactions from 0 (no risk) to 100 (confirmed fraud) using \
            all available tools before issuing a verdict.

            INVESTIGATION STEPS (follow in order):
            1. Call get_client_history to retrieve velocity, recent transactions, and behavioral stats.
            2. Call compute_risk_signals to get pre-computed contextual risk indicators.
            3. Call get_geo_context to get where the client is registered vs where the transaction is happening.
            4. Apply the scoring rubric below, combining all evidence.

            SCORING RUBRIC — add each applicable penalty, then subtract mitigating factors:

            RISK FACTORS (+pts):
              +35 — velocity spike: >5 transactions in the last 24h
              +25 — amount strongly deviates: current amount ≥ 3× card's historical average
              +20 — dawn window: transaction between 00:00 and 06:00 local time
              +20 — high rejection rate: >30% of recent transactions were rejected
              +15 — burst pattern: ≥3 transactions in the last 60 minutes
              +15 — high installment count (>6) on a high-value transaction (>R$500)
              +12 — round-number amount (e.g., R$500,00 or R$1000,00) — common in fraud
              +10 — no prior history: fewer than 3 transactions on record
              +10 — AMEX brand: statistically higher cloning and CNP risk
              +8  — weekend + high amount (>R$1000): reduced monitoring window
              +40 — geo: transaction location is geographically implausible (e.g., North Pole, middle of ocean)
              +30 — geo: transaction in a very distant country from client's home (e.g., São Paulo → Tokyo)
              +20 — geo: transaction in a different continent (e.g., São Paulo → London)
              +10 — geo: transaction in a neighboring country (e.g., São Paulo → Buenos Aires)

            MITIGATING FACTORS (−pts):
              −20 — amount is well below card's 30-day average (≤50% of avg)
              −15 — normal business hours (09:00–18:00, Mon–Fri) with clean history
              −10 — VISA or MASTER brand with clean history (0% rejection)
              −10 — low-value transaction (≤R$100) with stable history
              −10 — geo: transaction location matches or is near client's home city

            CALIBRATION:
              0–20   : low risk — approve
              21–49  : moderate risk — approve with monitoring
              50–74  : high risk — likely fraud, consider blocking
              75–100 : very high risk — block immediately

            After applying the rubric, respond with ONLY this JSON (no explanation):
            {"fraud_score": <integer 0-100>}
            """;

    private final BedrockRuntimeClient bedrockClient;
    private final TransactionHistoryPort historyPort;
    private final ClientProfilePort clientProfilePort;
    private final GeoLocationRegistry geoLocationRegistry;
    private final ObservationRegistry observationRegistry;
    private final String modelId;

    public BedrockFraudAnalysisAdapter(
            BedrockRuntimeClient bedrockClient,
            TransactionHistoryPort historyPort,
            ClientProfilePort clientProfilePort,
            GeoLocationRegistry geoLocationRegistry,
            ObservationRegistry observationRegistry,
            @Value("${bedrock.fraud-agent.model-id:us.anthropic.claude-haiku-4-5-20251001-v1:0}") String modelId) {
        this.bedrockClient = bedrockClient;
        this.historyPort = historyPort;
        this.clientProfilePort = clientProfilePort;
        this.geoLocationRegistry = geoLocationRegistry;
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
                Message toolResultMessage = executeToolCalls(assistantMessage.content(), request);
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

    private Message executeToolCalls(List<ContentBlock> contentBlocks, FraudAnalysisRequest request) {
        List<ContentBlock> toolResults = contentBlocks.stream()
                .filter(b -> b.toolUse() != null)
                .map(b -> executeToolCall(b.toolUse(), request))
                .toList();

        return Message.builder()
                .role(ConversationRole.USER)
                .content(toolResults)
                .build();
    }

    private ContentBlock executeToolCall(ToolUseBlock toolUse, FraudAnalysisRequest request) {
        String result = switch (toolUse.name()) {
            case "get_client_history" -> {
                String cardToken = toolUse.input().asMap().get("card_token").asString();
                TransactionHistory history = historyPort.findByCardToken(new CardToken(cardToken));
                yield buildHistoryResult(history, request.amount());
            }
            case "get_merchant_profile" -> {
                String brand = toolUse.input().asMap().get("brand").asString();
                MerchantProfile profile = MerchantProfile.forBrand(Brand.valueOf(brand));
                yield "brand=%s, cnp_risk=%s, risk_multiplier=%s"
                        .formatted(profile.brand(),
                                profile.brand() == Brand.AMEX ? "HIGH" : "STANDARD",
                                profile.riskMultiplier());
            }
            case "compute_risk_signals" -> buildRiskSignals(request);
            case "get_geo_context"      -> buildGeoContext(request);
            default -> throw new IllegalArgumentException("Unknown tool: " + toolUse.name());
        };

        return ContentBlock.builder()
                .toolResult(ToolResultBlock.builder()
                        .toolUseId(toolUse.toolUseId())
                        .content(ToolResultContentBlock.builder().text(result).build())
                        .build())
                .build();
    }

    private String buildGeoContext(FraudAnalysisRequest request) {
        GeoLocation txLocation = geoLocationRegistry.findByCode(request.locationCode()).orElse(null);
        ClientProfile profile = clientProfilePort.findByCardToken(request.cardToken()).orElse(null);
        GeoLocation homeLocation = (profile != null && profile.homeLocationCode() != null)
                ? geoLocationRegistry.findByCode(profile.homeLocationCode()).orElse(null)
                : null;

        if (txLocation == null) {
            return "geo_available=false, reason=unknown_location_code:" + request.locationCode();
        }

        String txDesc = "transaction_location=%s (%s) — coords=(%.4f, %.4f) — note: %s"
                .formatted(txLocation.city(), txLocation.country(),
                        txLocation.latitude(), txLocation.longitude(),
                        txLocation.riskHint() != null ? txLocation.riskHint() : "n/a");

        if (homeLocation == null) {
            return txDesc + "\nhome_location=unknown (no home registered for client)";
        }

        return txDesc + "\nhome_location=%s (%s) — coords=(%.4f, %.4f)"
                .formatted(homeLocation.city(), homeLocation.country(),
                        homeLocation.latitude(), homeLocation.longitude());
    }

    private String buildHistoryResult(TransactionHistory history, BigDecimal currentAmount) {
        List<TransactionSummary> recent = history.recent();

        if (recent.isEmpty()) {
            return "total_transactions=0, velocity_last_24h=0, avg_amount=N/A, " +
                   "rejection_rate_pct=0, burst_last_60min=0, total_30d=" + history.totalAmountLast30Days() +
                   ", amount_deviation=NEW_CARD";
        }

        BigDecimal avgAmount = recent.stream()
                .map(TransactionSummary::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(recent.size()), 2, RoundingMode.HALF_UP);

        BigDecimal maxAmount = recent.stream()
                .map(TransactionSummary::amount)
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);

        long rejectedCount = recent.stream()
                .filter(t -> t.status() == TransactionStatus.REJECTED).count();
        int rejectionRatePct = (int) (rejectedCount * 100L / recent.size());

        LocalDateTime now = LocalDateTime.now();
        long burstCount = recent.stream()
                .filter(t -> ChronoUnit.MINUTES.between(t.createdAt(), now) <= 60).count();

        long minutesSinceLast = recent.stream()
                .map(TransactionSummary::createdAt)
                .max(Comparator.naturalOrder())
                .map(last -> ChronoUnit.MINUTES.between(last, now))
                .orElse(-1L);

        String deviation = avgAmount.compareTo(BigDecimal.ZERO) == 0 ? "N/A"
                : currentAmount.divide(avgAmount, 2, RoundingMode.HALF_UP) + "x_avg";

        return ("total_transactions=%d, velocity_last_24h=%d, avg_amount=%s, max_amount=%s, " +
                "amount_deviation=%s, rejection_rate_pct=%d, burst_last_60min=%d, " +
                "minutes_since_last_tx=%d, total_30d=%s")
                .formatted(recent.size(), history.velocityLast24h(), avgAmount, maxAmount,
                        deviation, rejectionRatePct, burstCount, minutesSinceLast,
                        history.totalAmountLast30Days());
    }

    private String buildRiskSignals(FraudAnalysisRequest request) {
        LocalDateTime now = LocalDateTime.now();
        LocalTime time = now.toLocalTime();
        DayOfWeek dow = now.getDayOfWeek();

        String timeOfDay;
        if (time.isBefore(LocalTime.of(6, 0)))        timeOfDay = "DAWN (00:00-06:00) — HIGH RISK";
        else if (time.isBefore(LocalTime.of(9, 0)))   timeOfDay = "EARLY_MORNING (06:00-09:00) — MODERATE RISK";
        else if (time.isBefore(LocalTime.of(18, 0)))  timeOfDay = "BUSINESS_HOURS (09:00-18:00) — LOW RISK";
        else if (time.isBefore(LocalTime.of(22, 0)))  timeOfDay = "EVENING (18:00-22:00) — MODERATE RISK";
        else                                           timeOfDay = "NIGHT (22:00-00:00) — ELEVATED RISK";

        boolean isWeekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;

        BigDecimal amount = request.amount();
        String amountBracket;
        if (amount.compareTo(new BigDecimal("100")) <= 0)       amountBracket = "LOW (≤R$100)";
        else if (amount.compareTo(new BigDecimal("500")) <= 0)  amountBracket = "MEDIUM (R$100–500)";
        else if (amount.compareTo(new BigDecimal("2000")) <= 0) amountBracket = "HIGH (R$500–2000)";
        else                                                     amountBracket = "VERY_HIGH (>R$2000)";

        boolean isRoundAmount = amount.remainder(new BigDecimal("100")).compareTo(BigDecimal.ZERO) == 0;

        String installmentRisk;
        if (request.installments() > 6 && amount.compareTo(new BigDecimal("500")) > 0)
            installmentRisk = "HIGH — many installments on high-value tx";
        else if (request.installments() > 3)
            installmentRisk = "ELEVATED — multiple installments";
        else
            installmentRisk = "NORMAL";

        BigDecimal amountPerInstallment = request.installments() > 1
                ? amount.divide(BigDecimal.valueOf(request.installments()), 2, RoundingMode.HALF_UP)
                : amount;

        return ("time_of_day=%s, day_of_week=%s, is_weekend=%b, amount_bracket=%s, " +
                "is_round_amount=%b, installment_risk=%s, amount_per_installment=%s")
                .formatted(timeOfDay, dow, isWeekend, amountBracket,
                        isRoundAmount, installmentRisk, amountPerInstallment);
    }

    private ToolConfiguration buildToolConfig() {
        return ToolConfiguration.builder()
                .tools(
                        tool("get_client_history",
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
                                        .build()),
                        tool("get_merchant_profile",
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
                                        .build()),
                        tool("compute_risk_signals",
                                "Compute contextual risk signals: time-of-day risk, weekend flag, amount bracket, round-number flag, installment risk. No parameters needed.",
                                Document.mapBuilder()
                                        .putString("type", "object")
                                        .putDocument("properties", Document.mapBuilder().build())
                                        .putList("required", List.of())
                                        .build()),
                        tool("get_geo_context",
                                "Get the geolocation context: where the client lives (home) vs where this transaction is happening. Use this to assess geographic plausibility. No parameters needed.",
                                Document.mapBuilder()
                                        .putString("type", "object")
                                        .putDocument("properties", Document.mapBuilder().build())
                                        .putList("required", List.of())
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

    private int extractScore(String text) {
        Matcher matcher = SCORE_PATTERN.matcher(text);
        if (matcher.find()) {
            int score = Integer.parseInt(matcher.group(1));
            return Math.min(100, Math.max(0, score));
        }
        throw new IllegalStateException("Could not extract fraud_score from: " + text);
    }
}
