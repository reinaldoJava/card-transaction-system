package com.empresa.cardtransactionsystem.config;

import com.empresa.cardtransactionsystem.adapters.inbound.function.FraudResult;
import com.empresa.cardtransactionsystem.adapters.inbound.rest.dto.CardTransactionRequest;
import com.empresa.cardtransactionsystem.adapters.inbound.rest.dto.JwtResponse;
import com.empresa.cardtransactionsystem.adapters.inbound.rest.dto.LoginRequest;
import com.empresa.cardtransactionsystem.adapters.inbound.rest.dto.TransactionStatusResponse;
import com.empresa.cardtransactionsystem.adapters.outbound.lambda.JwtGenerator;
import com.empresa.cardtransactionsystem.adapters.outbound.lambda.dto.TokenExchangeRequest;
import com.empresa.cardtransactionsystem.adapters.outbound.lambda.dto.TokenExchangeResponse;
import com.empresa.cardtransactionsystem.adapters.outbound.observability.FlushableOtlpMeterRegistry;
import com.empresa.cardtransactionsystem.adapters.outbound.observability.TraceparentExtractor;
import com.empresa.cardtransactionsystem.adapters.outbound.observability.TransactionMetrics;
import com.empresa.cardtransactionsystem.application.orchestrator.TransactionOrchestrator;
import com.empresa.cardtransactionsystem.application.usecase.IdempotencyService;
import com.empresa.cardtransactionsystem.domain.model.FraudScore;
import com.empresa.cardtransactionsystem.domain.model.SagaPayload;
import com.empresa.cardtransactionsystem.domain.model.TransactionResult;
import com.empresa.cardtransactionsystem.domain.model.TransactionStatus;
import com.empresa.cardtransactionsystem.domain.model.ValidationResult;
import com.empresa.cardtransactionsystem.domain.ports.input.AnalyzeFraudUseCase;
import com.empresa.cardtransactionsystem.domain.ports.input.FraudFallbackUseCase;
import com.empresa.cardtransactionsystem.domain.ports.input.CompensationUseCase;
import com.empresa.cardtransactionsystem.domain.ports.input.GetTransactionStatusUseCase;
import com.empresa.cardtransactionsystem.domain.ports.input.LoginUseCase;
import com.empresa.cardtransactionsystem.domain.ports.input.ValidateBusinessRulesUseCase;
import com.empresa.cardtransactionsystem.domain.ports.input.ValidateTransactionUseCase;
import com.empresa.cardtransactionsystem.domain.ports.output.CallbackNotifierPort;
import com.empresa.cardtransactionsystem.domain.ports.output.DomainEventPublisherPort;
import com.empresa.cardtransactionsystem.domain.ports.output.TransactionRepositoryPort;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

@Configuration
public class FunctionsConfig {

    private final OpenTelemetry openTelemetry;
    private final MeterRegistry meterRegistry;

    public FunctionsConfig(OpenTelemetry openTelemetry, MeterRegistry meterRegistry) {
        this.openTelemetry = openTelemetry;
        this.meterRegistry = meterRegistry;
    }

    @Bean
    public Function<UUID, TransactionStatusResponse> getStatusFunction(
            GetTransactionStatusUseCase getTransactionStatusUseCase) {
        return wrapFn(correlationId ->
                getTransactionStatusUseCase.getStatus(correlationId)
                        .map(result -> new TransactionStatusResponse(correlationId, result.status(), result.reason()))
                        .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + correlationId)));
    }

    @Bean
    public Function<TokenExchangeRequest, TokenExchangeResponse> tokenExchange(
            JwtGenerator jwtGenerator) {
        return wrapFn(request -> new TokenExchangeResponse(jwtGenerator.generate(request.username())));
    }

    @Bean
    public Function<CardTransactionRequest, UUID> processTransactionFunction(
            TransactionOrchestrator orchestrator) {
        return wrapFn(orchestrator::orchestrate);
    }

    @Bean
    public Consumer<SagaPayload> compensationFunction(
            CompensationUseCase compensationUseCase,
            TraceparentExtractor traceparentExtractor) {
        return wrapConsumer(payload -> {
            try (Scope ignored = traceparentExtractor.restore(payload.traceparent())) {
                compensationUseCase.compensate(payload.correlationId());
            }
        });
    }

    @Bean
    public Function<SagaPayload, FraudScore> fraudAnalysisFunction(
            AnalyzeFraudUseCase analyzeFraudUseCase,
            FraudFallbackUseCase fraudFallbackUseCase,
            TraceparentExtractor traceparentExtractor,
            TransactionMetrics metrics) {
        return wrapFn(payload -> {
            try (Scope ignored = traceparentExtractor.restore(payload.traceparent())) {
                FraudScore score;
                try {
                    score = analyzeFraudUseCase.analyze(payload);
                } catch (Exception aiFailure) {
                    score = fraudFallbackUseCase.evaluate(payload);
                }
                metrics.recordFraudScore(score.score());
                return score;
            }
        });
    }

    @Bean
    public Function<LoginRequest, JwtResponse> loginFunction(LoginUseCase loginUseCase) {
        return wrapFn(request -> {
            var jwt = loginUseCase.login(request.username(), request.password());
            return new JwtResponse(jwt.value());
        });
    }

    @Bean
    public Consumer<FraudResult> updateStatusFunction(
            TransactionRepositoryPort repositoryPort,
            DomainEventPublisherPort eventPublisher,
            CompensationUseCase compensationUseCase,
            IdempotencyService idempotencyService,
            CallbackNotifierPort callbackNotifier,
            TraceparentExtractor traceparentExtractor,
            TransactionMetrics metrics,
            @Value("${fraud.agent.threshold:80}") int threshold) {
        return wrapConsumer(result -> {
            try (Scope ignored = traceparentExtractor.restore(result.traceparent())) {
                if (result.fraudScore().exceedsThreshold(threshold)) {
                    compensationUseCase.compensate(result.uuidTransaction());
                    TransactionResult txResult = TransactionResult.rejected(result.uuidTransaction(), "High fraud score");
                    idempotencyService.store(result.transactionId(), txResult);
                    SagaPayload finalPayload = publishFinalEvent(repositoryPort, eventPublisher, result.uuidTransaction(), result.traceparent());
                    if (finalPayload != null) callbackNotifier.notify(finalPayload, txResult);
                    metrics.recordFraudRejected();
                } else {
                    repositoryPort.updateStatus(result.uuidTransaction(), TransactionStatus.APPROVED);
                    TransactionResult txResult = TransactionResult.approved(result.uuidTransaction());
                    idempotencyService.store(result.transactionId(), txResult);
                    SagaPayload finalPayload = publishFinalEvent(repositoryPort, eventPublisher, result.uuidTransaction(), result.traceparent());
                    if (finalPayload != null) callbackNotifier.notify(finalPayload, txResult);
                    metrics.recordApproved();
                }
            }
        });
    }

    @Bean
    public Function<SagaPayload, ValidationResult> validateBusinessRulesFunction(
            ValidateBusinessRulesUseCase validateBusinessRulesUseCase,
            TraceparentExtractor traceparentExtractor) {
        return wrapFn(payload -> {
            try (Scope ignored = traceparentExtractor.restore(payload.traceparent())) {
                return validateBusinessRulesUseCase.validate(payload);
            }
        });
    }

    @Bean
    public Consumer<SagaPayload> validateTransactionFunction(
            ValidateTransactionUseCase validateTransactionUseCase,
            TraceparentExtractor traceparentExtractor) {
        return wrapConsumer(payload -> {
            try (Scope ignored = traceparentExtractor.restore(payload.traceparent())) {
                validateTransactionUseCase.validate(payload);
            }
        });
    }

    private SagaPayload publishFinalEvent(TransactionRepositoryPort repositoryPort,
                                          DomainEventPublisherPort eventPublisher,
                                          UUID correlationId, String traceparent) {
        return repositoryPort.findById(correlationId)
                .map(p -> p.withTraceparent(traceparent))
                .map(p -> { eventPublisher.publish(p); return p; })
                .orElse(null);
    }

    private <A, B> Function<A, B> wrapFn(Function<A, B> fn) {
        return input -> {
            B result = fn.apply(input);
            flush();
            return result;
        };
    }

    private <A> Consumer<A> wrapConsumer(Consumer<A> consumer) {
        return input -> {
            consumer.accept(input);
            flush();
        };
    }

    private void flush() {
        if (openTelemetry instanceof OpenTelemetrySdk sdk) {
            sdk.getSdkTracerProvider().forceFlush().join(2, TimeUnit.SECONDS);
        }
        if (meterRegistry instanceof FlushableOtlpMeterRegistry otlp) {
            otlp.flush();
        }
    }
}
