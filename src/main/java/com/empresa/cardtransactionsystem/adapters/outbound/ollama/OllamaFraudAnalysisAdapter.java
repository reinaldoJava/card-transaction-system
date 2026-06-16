package com.empresa.cardtransactionsystem.adapters.outbound.ollama;

import com.empresa.cardtransactionsystem.domain.model.Brand;
import com.empresa.cardtransactionsystem.domain.model.ClientProfile;
import com.empresa.cardtransactionsystem.domain.model.FraudCandidate;
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
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Profile("fraud-ollama")
public class OllamaFraudAnalysisAdapter implements FraudAnalysisPort {

    private static final Pattern SCORE_PATTERN = Pattern.compile("(\\d+)");
    private static final String MODEL = "mistral";

    private static final Logger log = LoggerFactory.getLogger(OllamaFraudAnalysisAdapter.class);

    private final RestTemplate restTemplate;
    private final TransactionHistoryPort historyPort;
    private final ClientProfilePort clientProfilePort;
    private final GeoLocationRegistry geoLocationRegistry;
    private final CircuitBreaker circuitBreaker;
    private final SsmClient ssmClient;
    private final String ollamaUrl;
    private final String systemPromptParameterName;

    private String systemPrompt;

    public OllamaFraudAnalysisAdapter(
            RestTemplate restTemplate,
            TransactionHistoryPort historyPort,
            ClientProfilePort clientProfilePort,
            GeoLocationRegistry geoLocationRegistry,
            @Qualifier("ollamaFraudCircuitBreaker") CircuitBreaker circuitBreaker,
            SsmClient ssmClient,
            @Value("${ollama.url:http://localhost:11434}") String ollamaUrl,
            @Value("${ollama.fraud-agent.system-prompt-parameter:/card-transaction-system/fraud/bedrock/system-prompt}")
            String systemPromptParameterName) {
        this.restTemplate = restTemplate;
        this.historyPort = historyPort;
        this.clientProfilePort = clientProfilePort;
        this.geoLocationRegistry = geoLocationRegistry;
        this.circuitBreaker = circuitBreaker;
        this.ssmClient = ssmClient;
        this.ollamaUrl = ollamaUrl;
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
        log.info("OllamaFraudAnalysisAdapter: system prompt loaded from SSM '{}'", systemPromptParameterName);
    }

    @Override
    public FraudScore analyze(FraudCandidate request) {
        TransactionHistory history = historyPort.findByCardToken(request.cardToken());
        MerchantProfile merchant = MerchantProfile.forBrand(request.brand());
        ClientProfile profile = clientProfilePort.findByCardToken(request.cardToken()).orElse(null);
        String userPrompt = buildUserPrompt(request, history, merchant, profile);
        String response = callOllama(systemPrompt, userPrompt);
        return FraudScore.of(extractScore(response));
    }

    private String buildUserPrompt(FraudCandidate request, TransactionHistory history,
                                   MerchantProfile merchant, ClientProfile profile) {
        return """
                Analyze this transaction for fraud and produce a fraud_score.

                --- TRANSACTION ---
                  amount:              R$%s
                  installments:        %d
                  brand:               %s
                  submitted_at:        %s

                --- CLIENT HISTORY ---
                %s

                --- MERCHANT PROFILE ---
                  brand:               %s
                  cnp_risk:            %s
                  risk_multiplier:     %s

                --- PRE-COMPUTED RISK SIGNALS ---
                %s

                --- GEO CONTEXT (reason about this — compare client home vs transaction location) ---
                %s

                Respond with ONLY the integer score (0–100):""".formatted(
                request.amount(),
                request.installments(),
                request.brand().name(),
                LocalDateTime.now(),
                buildHistorySection(history, request.amount()),
                merchant.brand(),
                merchant.brand() == Brand.AMEX ? "HIGH" : "STANDARD",
                merchant.riskMultiplier(),
                buildRiskSignalsSection(request),
                buildGeoSection(request, profile));
    }

    private String buildHistorySection(TransactionHistory history, BigDecimal currentAmount) {
        List<TransactionSummary> recent = history.recent();

        if (recent.isEmpty()) {
            return "  total_transactions:      0 (NEW CARD — no prior history)\n" +
                   "  velocity_last_24h:       0\n" +
                   "  total_30d:               R$" + history.totalAmountLast30Days();
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
                .filter(t -> t.status() == TransactionStatus.REJECTED)
                .count();
        int rejectionRatePct = (int) (rejectedCount * 100L / recent.size());

        LocalDateTime now = LocalDateTime.now();
        long burstCount = recent.stream()
                .filter(t -> ChronoUnit.MINUTES.between(t.createdAt(), now) <= 60)
                .count();

        long minutesSinceLast = recent.stream()
                .map(TransactionSummary::createdAt)
                .max(Comparator.naturalOrder())
                .map(last -> ChronoUnit.MINUTES.between(last, now))
                .orElse(-1L);

        String deviationLabel = avgAmount.compareTo(BigDecimal.ZERO) == 0
                ? "N/A"
                : currentAmount.divide(avgAmount, 2, RoundingMode.HALF_UP) + "x_avg";

        return ("  total_transactions:      %d\n" +
                "  velocity_last_24h:       %d\n" +
                "  avg_amount:              R$%s\n" +
                "  max_single_amount:       R$%s\n" +
                "  amount_deviation:        %s\n" +
                "  rejection_rate:          %d%%\n" +
                "  burst_last_60min:        %d tx\n" +
                "  minutes_since_last_tx:   %d\n" +
                "  total_30d:               R$%s")
                .formatted(recent.size(), history.velocityLast24h(),
                        avgAmount, maxAmount, deviationLabel,
                        rejectionRatePct, burstCount, minutesSinceLast,
                        history.totalAmountLast30Days());
    }

    private String buildRiskSignalsSection(FraudCandidate request) {
        LocalDateTime now = LocalDateTime.now();
        LocalTime time = now.toLocalTime();
        DayOfWeek dow = now.getDayOfWeek();

        String timeOfDay;
        if (time.isBefore(LocalTime.of(6, 0))) {
            timeOfDay = "DAWN (00:00-06:00) — HIGH RISK";
        } else if (time.isBefore(LocalTime.of(9, 0))) {
            timeOfDay = "EARLY_MORNING (06:00-09:00) — MODERATE RISK";
        } else if (time.isBefore(LocalTime.of(18, 0))) {
            timeOfDay = "BUSINESS_HOURS (09:00-18:00) — LOW RISK";
        } else if (time.isBefore(LocalTime.of(22, 0))) {
            timeOfDay = "EVENING (18:00-22:00) — MODERATE RISK";
        } else {
            timeOfDay = "NIGHT (22:00-00:00) — ELEVATED RISK";
        }

        boolean isWeekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;

        String amountBracket;
        BigDecimal amount = request.amount();
        if (amount.compareTo(new BigDecimal("100")) <= 0) {
            amountBracket = "LOW (≤R$100)";
        } else if (amount.compareTo(new BigDecimal("500")) <= 0) {
            amountBracket = "MEDIUM (R$100–500)";
        } else if (amount.compareTo(new BigDecimal("2000")) <= 0) {
            amountBracket = "HIGH (R$500–2000)";
        } else {
            amountBracket = "VERY_HIGH (>R$2000)";
        }

        boolean isRoundAmount = amount.remainder(new BigDecimal("100"))
                .compareTo(BigDecimal.ZERO) == 0;

        String installmentRisk;
        if (request.installments() > 6 && amount.compareTo(new BigDecimal("500")) > 0) {
            installmentRisk = "HIGH — many installments on high-value tx";
        } else if (request.installments() > 3) {
            installmentRisk = "ELEVATED — multiple installments";
        } else {
            installmentRisk = "NORMAL";
        }

        BigDecimal amountPerInstallment = request.installments() > 1
                ? amount.divide(BigDecimal.valueOf(request.installments()), 2, RoundingMode.HALF_UP)
                : amount;

        return ("  time_of_day:             %s\n" +
                "  day_of_week:             %s\n" +
                "  is_weekend:              %b\n" +
                "  amount_bracket:          %s\n" +
                "  is_round_amount:         %b\n" +
                "  installment_risk:        %s\n" +
                "  amount_per_installment:  R$%s")
                .formatted(timeOfDay, dow, isWeekend,
                        amountBracket, isRoundAmount,
                        installmentRisk, amountPerInstallment);
    }

    private String buildGeoSection(FraudCandidate request, ClientProfile profile) {
        GeoLocation txLocation = geoLocationRegistry.findByCode(request.locationCode()).orElse(null);

        if (txLocation == null) {
            return "  geo_available:   false — unknown location code: " + request.locationCode();
        }

        String txLine = "  transaction_location:    %s, %s (lat=%.4f, lon=%.4f)%s"
                .formatted(txLocation.city(), txLocation.country(),
                        txLocation.latitude(), txLocation.longitude(),
                        txLocation.riskHint() != null ? " — NOTE: " + txLocation.riskHint() : "");

        GeoLocation homeLocation = null;
        if (profile != null && profile.homeLocationCode() != null) {
            homeLocation = geoLocationRegistry.findByCode(profile.homeLocationCode()).orElse(null);
        }

        if (homeLocation == null) {
            return txLine + "\n  home_location:           unknown (no home registered for client)";
        }

        return txLine + "\n  home_location:           %s, %s (lat=%.4f, lon=%.4f)"
                .formatted(homeLocation.city(), homeLocation.country(),
                        homeLocation.latitude(), homeLocation.longitude());
    }

    private String callOllama(String system, String prompt) {
        OllamaRequest ollamaRequest = new OllamaRequest(MODEL, system, prompt, false);
        try {
            OllamaResponse response = circuitBreaker.executeSupplier(() ->
                    restTemplate.postForObject(ollamaUrl + "/api/generate", ollamaRequest, OllamaResponse.class));
            return response != null ? response.response() : "50";
        } catch (Exception e) {
            log.warn("Ollama indisponivel em {} ({}): propagando para a saga aplicar as regras de degrade.",
                    ollamaUrl, e.getMessage());
            throw e;
        }
    }

    private int extractScore(String response) {
        Matcher matcher = SCORE_PATTERN.matcher(response.trim());
        if (matcher.find()) {
            int score = Integer.parseInt(matcher.group(1));
            return Math.min(100, Math.max(0, score));
        }
        return 50;
    }

    record OllamaRequest(String model, String system, String prompt, boolean stream) {}

    record OllamaResponse(String response) {}
}
