package com.empresa.cardtransactionsystem.application.usecase;

import com.empresa.cardtransactionsystem.domain.model.ClientProfile;
import com.empresa.cardtransactionsystem.domain.model.FraudScore;
import com.empresa.cardtransactionsystem.domain.model.GeoLocation;
import com.empresa.cardtransactionsystem.domain.model.GeoRiskLevel;
import com.empresa.cardtransactionsystem.domain.model.SagaPayload;
import com.empresa.cardtransactionsystem.domain.ports.input.FraudFallbackUseCase;
import com.empresa.cardtransactionsystem.domain.ports.output.ClientProfilePort;
import com.empresa.cardtransactionsystem.domain.service.GeoDistanceCalculator;
import com.empresa.cardtransactionsystem.domain.service.GeoLocationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * Regras estáticas de degrade aplicadas quando a IA de fraude está indisponível:
 *   1. Geo distância EXTREMA (home vs. transação) → BLOCK (score 100)
 *   2. Madrugada (00:00–06:00) → BLOCK (score 100)
 *   3. Cliente VIP + amount ≤ R$500 → PASS (score 0)
 *   4. Cliente comum + amount ≤ R$100 → PASS (score 0)
 *   5. Caso contrário → BLOCK (score 100)
 */
@Service
public class FraudFallbackService implements FraudFallbackUseCase {

    private static final Logger log = LoggerFactory.getLogger(FraudFallbackService.class);

    private static final BigDecimal VIP_FALLBACK_LIMIT     = new BigDecimal("500.00");
    private static final BigDecimal REGULAR_FALLBACK_LIMIT = new BigDecimal("100.00");
    private static final LocalTime  DAWN_START             = LocalTime.of(0, 0);
    private static final LocalTime  DAWN_END               = LocalTime.of(6, 0);

    private final ClientProfilePort clientProfilePort;
    private final GeoLocationRegistry geoLocationRegistry;

    public FraudFallbackService(ClientProfilePort clientProfilePort, GeoLocationRegistry geoLocationRegistry) {
        this.clientProfilePort = clientProfilePort;
        this.geoLocationRegistry = geoLocationRegistry;
    }

    @Override
    public FraudScore evaluate(SagaPayload payload) {
        log.warn("Fraud analysis unavailable — applying static fallback rules for correlationId={}",
                payload.correlationId());

        if (payload.locationCode() != null) {
            GeoLocation txLocation = geoLocationRegistry.findByCode(payload.locationCode()).orElse(null);
            ClientProfile profileForGeo = clientProfilePort.findByCardToken(payload.cardToken()).orElse(null);
            GeoLocation homeLocation = (profileForGeo != null && profileForGeo.homeLocationCode() != null)
                    ? geoLocationRegistry.findByCode(profileForGeo.homeLocationCode()).orElse(null)
                    : null;
            if (txLocation != null && homeLocation != null) {
                GeoRiskLevel geoRisk = GeoDistanceCalculator.riskLevel(homeLocation, txLocation);
                if (geoRisk == GeoRiskLevel.EXTREME) {
                    log.warn("Fallback: BLOCK — EXTREME geo distance for correlationId={}", payload.correlationId());
                    return new FraudScore(100);
                }
            }
        }

        LocalTime now = LocalTime.now();
        if (!now.isBefore(DAWN_START) && now.isBefore(DAWN_END)) {
            log.warn("Fallback: BLOCK — madrugada window ({}) for correlationId={}", now, payload.correlationId());
            return new FraudScore(100);
        }

        ClientProfile profile = clientProfilePort.findByCardToken(payload.cardToken()).orElse(null);

        if (profile != null && profile.vip() && payload.amount().compareTo(VIP_FALLBACK_LIMIT) <= 0) {
            log.info("Fallback: PASS — VIP client, amount={} <= {} for correlationId={}",
                    payload.amount(), VIP_FALLBACK_LIMIT, payload.correlationId());
            return new FraudScore(0);
        }

        if ((profile == null || !profile.vip()) && payload.amount().compareTo(REGULAR_FALLBACK_LIMIT) <= 0) {
            log.info("Fallback: PASS — regular client, amount={} <= {} for correlationId={}",
                    payload.amount(), REGULAR_FALLBACK_LIMIT, payload.correlationId());
            return new FraudScore(0);
        }

        log.warn("Fallback: BLOCK — no rule matched, amount={} for correlationId={}",
                payload.amount(), payload.correlationId());
        return new FraudScore(100);
    }
}
