package com.empresa.cardtransactionsystem.adapters.outbound.ollama;

import com.empresa.cardtransactionsystem.domain.model.Brand;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

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

    private final RestTemplate restTemplate;
    private final TransactionHistoryPort historyPort;
    private final ClientProfilePort clientProfilePort;
    private final GeoLocationRegistry geoLocationRegistry;
    private final String ollamaUrl;

    public OllamaFraudAnalysisAdapter(
            RestTemplate restTemplate,
            TransactionHistoryPort historyPort,
            ClientProfilePort clientProfilePort,
            GeoLocationRegistry geoLocationRegistry,
            @Value("${ollama.url:http://localhost:11434}") String ollamaUrl) {
        this.restTemplate = restTemplate;
        this.historyPort = historyPort;
        this.clientProfilePort = clientProfilePort;
        this.geoLocationRegistry = geoLocationRegistry;
        this.ollamaUrl = ollamaUrl;
    }

    @Override
    public FraudScore analyze(FraudAnalysisRequest request) {
        TransactionHistory history = historyPort.findByCardToken(request.cardToken());
        MerchantProfile merchant = MerchantProfile.forBrand(request.brand());
        ClientProfile profile = clientProfilePort.findByCardToken(request.cardToken()).orElse(null);
        String prompt = buildPrompt(request, history, merchant, profile);
        String response = callOllama(prompt);
        return FraudScore.of(extractScore(response));
    }

    private String buildPrompt(FraudAnalysisRequest request, TransactionHistory history,
                               MerchantProfile merchant, ClientProfile profile) {
        return """
                You are a senior fraud analyst at a Brazilian payment processor.
                Score the transaction below from 0 (no risk) to 100 (confirmed fraud).
                Apply the scoring rubric precisely. Respond with ONLY an integer, nothing else.

                SCORING RUBRIC — sum applicable penalties, then subtract mitigating factors:

                RISK FACTORS (+pts):
                  +35 — velocity spike: >5 transactions in the last 24h
                  +25 — amount strongly deviates: current amount ≥ 3× card's historical average
                  +20 — dawn window: transaction between 00:00 and 06:00
                  +20 — high rejection rate: >30%% of recent transactions were rejected
                  +15 — burst pattern: ≥3 transactions in the last 60 minutes
                  +15 — high installment count (>6) on a high-value transaction (>R$500)
                  +12 — round-number amount (e.g., R$500,00 or R$1000,00)
                  +10 — no prior history: fewer than 3 transactions on record
                  +10 — AMEX brand: statistically higher cloning and CNP risk
                  +8  — weekend + high amount (>R$1000)
                  +40 — geo: transaction location is geographically implausible (e.g., North Pole, middle of ocean, uninhabited region)
                  +30 — geo: transaction in a very distant country from client's home (e.g., São Paulo → Tokyo)
                  +20 — geo: transaction in a different continent (e.g., São Paulo → London)
                  +10 — geo: transaction in a neighboring country (e.g., São Paulo → Buenos Aires)

                MITIGATING FACTORS (−pts):
                  −20 — amount is well below card's 30-day average (≤50%% of avg)
                  −15 — normal business hours (09:00–18:00, Mon–Fri) with clean history
                  −10 — VISA or MASTER brand with clean history (0%% rejection)
                  −10 — low-value transaction (≤R$100) with stable history
                  −10 — geo: transaction location matches or is near client's home city

                CALIBRATION:
                  0–20 : low risk    |  21–49 : moderate  |  50–74 : high  |  75–100 : very high

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

    private String buildRiskSignalsSection(FraudAnalysisRequest request) {
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

    private String buildGeoSection(FraudAnalysisRequest request, ClientProfile profile) {
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

    private String callOllama(String prompt) {
        OllamaRequest ollamaRequest = new OllamaRequest(MODEL, prompt, false);
        OllamaResponse response = restTemplate.postForObject(
                ollamaUrl + "/api/generate",
                ollamaRequest,
                OllamaResponse.class);
        return response != null ? response.response() : "50";
    }

    private int extractScore(String response) {
        Matcher matcher = SCORE_PATTERN.matcher(response.trim());
        if (matcher.find()) {
            int score = Integer.parseInt(matcher.group(1));
            return Math.min(100, Math.max(0, score));
        }
        return 50;
    }

    record OllamaRequest(String model, String prompt, boolean stream) {}

    record OllamaResponse(String response) {}
}
