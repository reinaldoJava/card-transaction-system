package com.empresa.cardtransactionsystem.adapters.outbound.temporal;

import com.empresa.cardtransactionsystem.application.usecase.IdempotencyService;
import com.empresa.cardtransactionsystem.domain.model.FraudScore;
import com.empresa.cardtransactionsystem.domain.model.SagaPayload;
import com.empresa.cardtransactionsystem.domain.model.TransactionResult;
import com.empresa.cardtransactionsystem.domain.model.TransactionStatus;
import com.empresa.cardtransactionsystem.domain.model.ValidationResult;
import com.empresa.cardtransactionsystem.domain.ports.input.AnalyzeFraudUseCase;
import com.empresa.cardtransactionsystem.domain.ports.input.CompensationUseCase;
import com.empresa.cardtransactionsystem.domain.ports.input.FraudFallbackUseCase;
import com.empresa.cardtransactionsystem.domain.ports.input.ValidateBusinessRulesUseCase;
import com.empresa.cardtransactionsystem.domain.ports.input.ValidateTransactionUseCase;
import com.empresa.cardtransactionsystem.domain.ports.output.CallbackNotifierPort;
import com.empresa.cardtransactionsystem.domain.ports.output.DomainEventPublisherPort;
import com.empresa.cardtransactionsystem.domain.ports.output.TransactionRepositoryPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Profile("saga-temporal")
public class TransactionSagaActivitiesImpl implements TransactionSagaActivities {

    private final ValidateTransactionUseCase validateTransactionUseCase;
    private final ValidateBusinessRulesUseCase validateBusinessRulesUseCase;
    private final AnalyzeFraudUseCase analyzeFraudUseCase;
    private final FraudFallbackUseCase fraudFallbackUseCase;
    private final CompensationUseCase compensationUseCase;
    private final TransactionRepositoryPort transactionRepository;
    private final DomainEventPublisherPort eventPublisher;
    private final CallbackNotifierPort callbackNotifier;
    private final IdempotencyService idempotencyService;

    public TransactionSagaActivitiesImpl(
            ValidateTransactionUseCase validateTransactionUseCase,
            ValidateBusinessRulesUseCase validateBusinessRulesUseCase,
            AnalyzeFraudUseCase analyzeFraudUseCase,
            FraudFallbackUseCase fraudFallbackUseCase,
            CompensationUseCase compensationUseCase,
            TransactionRepositoryPort transactionRepository,
            DomainEventPublisherPort eventPublisher,
            CallbackNotifierPort callbackNotifier,
            IdempotencyService idempotencyService) {
        this.validateTransactionUseCase = validateTransactionUseCase;
        this.validateBusinessRulesUseCase = validateBusinessRulesUseCase;
        this.analyzeFraudUseCase = analyzeFraudUseCase;
        this.fraudFallbackUseCase = fraudFallbackUseCase;
        this.compensationUseCase = compensationUseCase;
        this.transactionRepository = transactionRepository;
        this.eventPublisher = eventPublisher;
        this.callbackNotifier = callbackNotifier;
        this.idempotencyService = idempotencyService;
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

    @Override
    public FraudScore evaluateFraudFallback(SagaPayload payload) {
        return fraudFallbackUseCase.evaluate(payload);
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
