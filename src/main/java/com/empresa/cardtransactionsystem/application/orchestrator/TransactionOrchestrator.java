package com.empresa.cardtransactionsystem.application.orchestrator;

import com.empresa.cardtransactionsystem.adapters.inbound.rest.dto.CardTransactionRequest;
import com.empresa.cardtransactionsystem.adapters.inbound.rest.mapper.CardDataMapper;
import com.empresa.cardtransactionsystem.adapters.outbound.observability.TraceparentExtractor;
import com.empresa.cardtransactionsystem.adapters.outbound.observability.TransactionMetrics;
import com.empresa.cardtransactionsystem.application.usecase.IdempotencyService;
import com.empresa.cardtransactionsystem.domain.model.*;
import com.empresa.cardtransactionsystem.domain.ports.output.CachePort;
import com.empresa.cardtransactionsystem.domain.ports.output.CallbackNotifierPort;
import com.empresa.cardtransactionsystem.domain.ports.output.DomainEventPublisherPort;
import com.empresa.cardtransactionsystem.domain.ports.output.SagaStarterPort;
import com.empresa.cardtransactionsystem.domain.ports.output.TransactionRepositoryPort;
import com.empresa.cardtransactionsystem.domain.service.CardValidationService;
import com.empresa.cardtransactionsystem.domain.service.GeoLocationRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class TransactionOrchestrator {

    private final SagaStarterPort sagaStarterPort;
    private final TransactionRepositoryPort transactionRepository;
    private final CardValidationService cardValidationService;
    private final IdempotencyService idempotencyService;
    private final CachePort cachePort;
    private final DomainEventPublisherPort eventPublisher;
    private final CallbackNotifierPort callbackNotifier;
    private final TraceparentExtractor traceparentExtractor;
    private final TransactionMetrics metrics;
    private final GeoLocationRegistry geoLocationRegistry;
    private final int fraudScoreThreshold;
    private final CardDataMapper cardDataMapper;

    public TransactionOrchestrator(
            SagaStarterPort sagaStarterPort,
            TransactionRepositoryPort transactionRepository,
            CardValidationService cardValidationService,
            IdempotencyService idempotencyService,
            CachePort cachePort,
            DomainEventPublisherPort eventPublisher,
            CallbackNotifierPort callbackNotifier,
            TraceparentExtractor traceparentExtractor,
            TransactionMetrics metrics,
            GeoLocationRegistry geoLocationRegistry,
            @Value("${fraud.agent.threshold:80}") int fraudScoreThreshold,
            CardDataMapper cardDataMapper) {
        this.sagaStarterPort = sagaStarterPort;
        this.transactionRepository = transactionRepository;
        this.cardValidationService = cardValidationService;
        this.idempotencyService = idempotencyService;
        this.cachePort = cachePort;
        this.eventPublisher = eventPublisher;
        this.callbackNotifier = callbackNotifier;
        this.traceparentExtractor = traceparentExtractor;
        this.metrics = metrics;
        this.geoLocationRegistry = geoLocationRegistry;
        this.fraudScoreThreshold = fraudScoreThreshold;
        this.cardDataMapper = cardDataMapper;
    }

    public UUID orchestrate(CardTransactionRequest request) {
        Optional<TransactionResult> cached = idempotencyService.check(request.transactionId());
        if (cached.isPresent()) {
            return cached.get().correlationId();
        }

        CardData cardData = cardDataMapper.toDomain(request.cardDataRequest());
        CardToken cardToken = cardValidationService.tokenize(cardData);
        String callbackUrl = request.callbackUrl();
        String locationCode = resolveLocationCode(request.locationCode());

        Optional<FraudScore> highFraudScore = cachePort.getFraudScore(cardToken)
                .filter(score -> score.exceedsThreshold(fraudScoreThreshold));

        if (highFraudScore.isPresent()) {
            SagaPayload rejected = SagaPayload.rejected(
                    request.transactionId(), request.uuidTransaction(),
                    cardToken, request.amount(), request.installments(), cardData.brand(), callbackUrl)
                    .withLocationCode(locationCode);
            transactionRepository.save(rejected);
            eventPublisher.publish(rejected);
            TransactionResult result = TransactionResult.rejected(request.uuidTransaction(),
                    "High fraud score in cache: " + highFraudScore.get().score());
            idempotencyService.store(request.transactionId(), result);
            callbackNotifier.notify(rejected, result);
            metrics.recordCacheRejected();
            return rejected.correlationId();
        }

        SagaPayload payload = SagaPayload.pending(
                request.transactionId(), request.uuidTransaction(),
                cardToken, request.amount(), request.installments(), cardData.brand(), callbackUrl)
                .withTraceparent(traceparentExtractor.extract())
                .withLocationCode(locationCode);

        transactionRepository.save(payload);
        sagaStarterPort.start(payload);
        eventPublisher.publish(payload);
        metrics.recordSubmitted();
        return payload.correlationId();
    }

    private String resolveLocationCode(String requested) {
        if (requested != null && !requested.isBlank()) {
            return geoLocationRegistry.findByCode(requested)
                    .map(GeoLocation::code)
                    .orElse(requested);
        }
        return geoLocationRegistry.random().code();
    }
}
