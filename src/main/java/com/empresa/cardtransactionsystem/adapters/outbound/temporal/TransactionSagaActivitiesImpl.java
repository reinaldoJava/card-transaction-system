package com.empresa.cardtransactionsystem.adapters.outbound.temporal;

import com.empresa.cardtransactionsystem.application.usecase.IdempotencyService;
import com.empresa.cardtransactionsystem.domain.model.FraudScore;
import com.empresa.cardtransactionsystem.domain.model.SagaPayload;
import com.empresa.cardtransactionsystem.domain.model.TransactionResult;
import com.empresa.cardtransactionsystem.domain.model.TransactionStatus;
import com.empresa.cardtransactionsystem.domain.model.ValidationResult;
import com.empresa.cardtransactionsystem.domain.model.ClientProfile;
import com.empresa.cardtransactionsystem.domain.model.GeoLocation;
import com.empresa.cardtransactionsystem.domain.model.GeoRiskLevel;
import com.empresa.cardtransactionsystem.domain.ports.input.AnalyzeFraudUseCase;
import com.empresa.cardtransactionsystem.domain.service.GeoDistanceCalculator;
import com.empresa.cardtransactionsystem.domain.service.GeoLocationRegistry;
import com.empresa.cardtransactionsystem.domain.ports.input.CompensationUseCase;
import com.empresa.cardtransactionsystem.domain.ports.input.ValidateBusinessRulesUseCase;
import com.empresa.cardtransactionsystem.domain.ports.input.ValidateTransactionUseCase;
import com.empresa.cardtransactionsystem.domain.ports.output.CallbackNotifierPort;
import com.empresa.cardtransactionsystem.domain.ports.output.ClientProfilePort;
import com.empresa.cardtransactionsystem.domain.ports.output.DomainEventPublisherPort;
import com.empresa.cardtransactionsystem.domain.ports.output.TransactionRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

@Component
@Profile("saga-temporal")
public class TransactionSagaActivitiesImpl implements TransactionSagaActivities {

    private static final Logger log = LoggerFactory.getLogger(TransactionSagaActivitiesImpl.class);

    // Fallback thresholds
    private static final BigDecimal VIP_FALLBACK_LIMIT    = new BigDecimal("500.00");
    private static final BigDecimal REGULAR_FALLBACK_LIMIT = new BigDecimal("100.00");
    private static final LocalTime  DAWN_START             = LocalTime.of(0, 0);
    private static final LocalTime  DAWN_END               = LocalTime.of(6, 0);

    private final ValidateTransactionUseCase validateTransactionUseCase;
    private final ValidateBusinessRulesUseCase validateBusinessRulesUseCase;
    private final AnalyzeFraudUseCase analyzeFraudUseCase;
    private final CompensationUseCase compensationUseCase;
    private final TransactionRepositoryPort transactionRepository;
    private final DomainEventPublisherPort eventPublisher;
    private final CallbackNotifierPort callbackNotifier;
    private final IdempotencyService idempotencyService;
    private final ClientProfilePort clientProfilePort;
    private final GeoLocationRegistry geoLocationRegistry;

    public TransactionSagaActivitiesImpl(
            ValidateTransactionUseCase validateTransactionUseCase,
            ValidateBusinessRulesUseCase validateBusinessRulesUseCase,
            AnalyzeFraudUseCase analyzeFraudUseCase,
            CompensationUseCase compensationUseCase,
            TransactionRepositoryPort transactionRepository,
            DomainEventPublisherPort eventPublisher,
            CallbackNotifierPort callbackNotifier,
            IdempotencyService idempotencyService,
            ClientProfilePort clientProfilePort,
            GeoLocationRegistry geoLocationRegistry) {
        this.validateTransactionUseCase = validateTransactionUseCase;
        this.validateBusinessRulesUseCase = validateBusinessRulesUseCase;
        this.analyzeFraudUseCase = analyzeFraudUseCase;
        this.compensationUseCase = compensationUseCase;
        this.transactionRepository = transactionRepository;
        this.eventPublisher = eventPublisher;
        this.callbackNotifier = callbackNotifier;
        this.idempotencyService = idempotencyService;
        this.clientProfilePort = clientProfilePort;
        this.geoLocationRegistry = geoLocationRegistry;
    }

    @Override
    public void validateTransaction(SagaPayload payload) {
        validateTransactionUseCase.validate(payload);
    }

    @Override
    public ValidationResult validateBusinessRules(SagaPayload payload) {
        return validateBusinessRulesUseCase.validate(payload);
    }

    @Override
    public FraudScore analyzeFraud(SagaPayload payload) {
        return analyzeFraudUseCase.analyze(payload);
    }

    /**
     * Degraded-mode fallback rules (Rule-Based Fallback / Decisioning Fallback Policy):
     *   1. Madrugada (00:00–06:00) → BLOCK ALL (score 100)
     *   2. VIP client + amount ≤ R$500 → PASS (score 0)
     *   3. Regular client + amount ≤ R$100 → PASS (score 0)
     *   4. Otherwise → REJECT (score 100)
     */
    @Override
    public FraudScore evaluateFraudFallback(SagaPayload payload) {
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

        ClientProfile profile = clientProfilePort.findByCardToken(payload.cardToken())
                .orElse(null);

        if (profile != null && profile.vip() && payload.amount().compareTo(VIP_FALLBACK_LIMIT) <= 0) {
            log.info("Fallback: PASS — VIP client, amount={} ≤ {} for correlationId={}",
                    payload.amount(), VIP_FALLBACK_LIMIT, payload.correlationId());
            return new FraudScore(0);
        }

        if ((profile == null || !profile.vip()) && payload.amount().compareTo(REGULAR_FALLBACK_LIMIT) <= 0) {
            log.info("Fallback: PASS — regular client, amount={} ≤ {} for correlationId={}",
                    payload.amount(), REGULAR_FALLBACK_LIMIT, payload.correlationId());
            return new FraudScore(0);
        }

        log.warn("Fallback: BLOCK — no rule matched, amount={} for correlationId={}",
                payload.amount(), payload.correlationId());
        return new FraudScore(100);
    }

    @Override
    public void approveTransaction(String transactionId, UUID correlationId, String traceparent) {
        transactionRepository.updateStatus(correlationId, TransactionStatus.APPROVED);
        TransactionResult result = TransactionResult.approved(correlationId);
        idempotencyService.store(transactionId, result);
        transactionRepository.findById(correlationId)
                .map(p -> p.withTraceparent(traceparent))
                .ifPresent(p -> {
                    eventPublisher.publish(p);
                    callbackNotifier.notify(p, result);
                });
    }

    @Override
    public void rejectTransaction(String transactionId, UUID correlationId, String reason, String traceparent) {
        compensationUseCase.compensate(correlationId);
        transactionRepository.updateStatusAndReason(correlationId, TransactionStatus.REJECTED, reason);
        TransactionResult result = TransactionResult.rejected(correlationId, reason);
        idempotencyService.store(transactionId, result);
        transactionRepository.findById(correlationId)
                .map(p -> p.withTraceparent(traceparent))
                .ifPresent(p -> {
                    eventPublisher.publish(p);
                    callbackNotifier.notify(p, result);
                });
    }
}
