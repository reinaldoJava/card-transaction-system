package com.empresa.cardtransactionsystem.adapters.inbound.function;

import com.empresa.cardtransactionsystem.adapters.outbound.observability.TraceparentExtractor;
import com.empresa.cardtransactionsystem.adapters.outbound.observability.TransactionMetrics;
import com.empresa.cardtransactionsystem.domain.model.Brand;
import com.empresa.cardtransactionsystem.domain.model.CardToken;
import com.empresa.cardtransactionsystem.domain.model.FraudScore;
import com.empresa.cardtransactionsystem.domain.model.SagaPayload;
import com.empresa.cardtransactionsystem.domain.model.TransactionStatus;
import com.empresa.cardtransactionsystem.domain.ports.input.AnalyzeFraudUseCase;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FraudAnalysisFunction")
class FraudAnalysisFunctionTest {

    @Mock private AnalyzeFraudUseCase analyzeFraudUseCase;
    @Mock private TraceparentExtractor traceparentExtractor;
    @Mock private TransactionMetrics metrics;

    private Function<SagaPayload, FraudScore> function;

    @BeforeEach
    void setUp() {
        lenient().when(traceparentExtractor.restore(any())).thenReturn(Scope.noop());
        function = new FunctionsConfig(OpenTelemetry.noop(), new SimpleMeterRegistry())
                .fraudAnalysisFunction(analyzeFraudUseCase, traceparentExtractor, metrics);
    }

    @Test
    @DisplayName("should delegate to use case and return score")
    void shouldDelegateAndReturnScore() {
        when(analyzeFraudUseCase.analyze(any())).thenReturn(FraudScore.of(30));

        FraudScore score = function.apply(payload());

        assertThat(score.score()).isEqualTo(30);
        verify(analyzeFraudUseCase).analyze(any());
    }

    private SagaPayload payload() {
        return new SagaPayload("TXN-001", UUID.randomUUID(), new CardToken("tok"),
                new BigDecimal("500.00"), 3, Brand.VISA,
                TransactionStatus.PENDING, LocalDateTime.now(), null, null);
    }
}
