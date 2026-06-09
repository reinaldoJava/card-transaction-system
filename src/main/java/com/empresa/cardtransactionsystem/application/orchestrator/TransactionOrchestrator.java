package com.empresa.cardtransactionsystem.application.orchestrator;

import com.empresa.cardtransactionsystem.adapters.inbound.rest.dto.CardTransactionRequest;
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
    private final int fraudScoreThreshold;

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
            @Value("${fraud.agent.threshold:80}") int fraudScoreThreshold) {
        this.sagaStarterPort = sagaStarterPort;
        this.transactionRepository = transactionRepository;
        this.cardValidationService = cardValidationService;
        this.idempotencyService = idempotencyService;
        this.cachePort = cachePort;
        this.eventPublisher = eventPublisher;
        this.callbackNotifier = callbackNotifier;
        this.traceparentExtractor = traceparentExtractor;
        this.metrics = metrics;
        this.fraudScoreThreshold = fraudScoreThreshold;
    }

    public UUID orchestrate(CardTransactionRequest request) {
        Optional<TransactionResult> cached = idempotencyService.check(request.transactionId());
        if (cached.isPresent()) {
            return cached.get().correlationId();
        }

        CardData cardData = buildCardData(request);
        CardToken cardToken = cardValidationService.tokenize(cardData);
        Brand brand = Brand.valueOf(request.cardDataRequest().brand());
        String callbackUrl = request.callbackUrl();

        Optional<FraudScore> highFraudScore = cachePort.getFraudScore(cardToken)
                .filter(score -> score.exceedsThreshold(fraudScoreThreshold));

        if (highFraudScore.isPresent()) {
            SagaPayload rejected = SagaPayload.rejected(
                    request.transactionId(), request.uuidTransaction(),
                    cardToken, request.amount(), request.installments(), brand, callbackUrl);
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
                cardToken, request.amount(), request.installments(), brand, callbackUrl)
                .withTraceparent(traceparentExtractor.extract());

        transactionRepository.save(payload);
        sagaStarterPort.start(payload);
        eventPublisher.publish(payload);
        metrics.recordSubmitted();
        return payload.correlationId();
    }

    private CardData buildCardData(CardTransactionRequest request) {
        return new CardData(
                new CardNumber(request.cardDataRequest().number()),
                new Cvv(request.cardDataRequest().cvv()),
                request.cardDataRequest().name(),
                Brand.valueOf(request.cardDataRequest().brand())
        );
    }
}
