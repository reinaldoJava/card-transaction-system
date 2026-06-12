package com.empresa.cardtransactionsystem.adapters.outbound.bedrock;

import com.empresa.cardtransactionsystem.domain.model.ClientProfile;
import com.empresa.cardtransactionsystem.domain.model.FraudCandidate;
import com.empresa.cardtransactionsystem.domain.model.GeoLocation;
import com.empresa.cardtransactionsystem.domain.model.TransactionHistory;
import com.empresa.cardtransactionsystem.domain.model.TimeOfDayRisk;
import com.empresa.cardtransactionsystem.domain.model.TransactionSummary;
import com.empresa.cardtransactionsystem.domain.ports.output.ClientProfilePort;
import com.empresa.cardtransactionsystem.domain.service.GeoLocationRegistry;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDateTime;

@Component
public class FraudContextBuilder {

    private final ClientProfilePort clientProfilePort;
    private final GeoLocationRegistry geoLocationRegistry;

    public FraudContextBuilder(ClientProfilePort clientProfilePort, GeoLocationRegistry geoLocationRegistry) {
        this.clientProfilePort = clientProfilePort;
        this.geoLocationRegistry = geoLocationRegistry;
    }

    public String buildHistoryResult(TransactionHistory history, BigDecimal currentAmount) {
        if (history.recent().isEmpty()) {
            return "total_transactions=0, velocity_last_24h=0, avg_amount=N/A, " +
                   "rejection_rate_pct=0, burst_last_60min=0, total_30d=" + history.totalAmountLast30Days() +
                   ", amount_deviation=NEW_CARD";
        }

        BigDecimal avgAmount = history.averageAmount();
        String deviation = avgAmount.compareTo(BigDecimal.ZERO) == 0 ? "N/A"
                : currentAmount.divide(avgAmount, 2, RoundingMode.HALF_UP) + "x_avg";

        return ("total_transactions=%d, velocity_last_24h=%d, avg_amount=%s, max_amount=%s, " +
                "amount_deviation=%s, rejection_rate_pct=%d, burst_last_60min=%d, " +
                "minutes_since_last_tx=%d, total_30d=%s")
                .formatted(history.recent().size(), history.velocityLast24h(), avgAmount, history.maxAmount(),
                        deviation, history.rejectionRatePct(), history.burstCountLastHour(),
                        history.minutesSinceLastTransaction(), history.totalAmountLast30Days());
    }

    public String buildRiskSignals(FraudCandidate candidate) {
        LocalDateTime now = LocalDateTime.now();
        DayOfWeek dow = now.getDayOfWeek();
        boolean isWeekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
        TimeOfDayRisk timeOfDayRisk = TimeOfDayRisk.from(now.toLocalTime());

        return """
                time_of_day=%s, day_of_week=%s, is_weekend=%b, \
                amount_bracket=%s, is_round_amount=%b, \
                installment_risk=%s, amount_per_installment=%s"""
                .formatted(timeOfDayRisk, dow, isWeekend,
                        candidate.amountBracket(), candidate.isRoundAmount(),
                        candidate.installmentRisk(), candidate.amountPerInstallment());
    }

    public String buildGeoContext(FraudCandidate request) {
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
}
