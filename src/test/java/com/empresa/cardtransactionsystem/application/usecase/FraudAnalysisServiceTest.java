package com.empresa.cardtransactionsystem.application.usecase;

import com.empresa.cardtransactionsystem.domain.model.*;
import com.empresa.cardtransactionsystem.fixture.SagaPayloadFixture;
import com.empresa.cardtransactionsystem.domain.ports.output.CachePort;
import com.empresa.cardtransactionsystem.domain.ports.output.FraudAnalysisPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FraudAnalysisService")
class FraudAnalysisServiceTest {

    @Mock private FraudAnalysisPort fraudAnalysisPort;
    @Mock private CachePort cachePort;

    private FraudAnalysisService service;

    private static final CardToken TOKEN = new CardToken("token-uuid");

    @BeforeEach
    void setUp() {
        service = new FraudAnalysisService(fraudAnalysisPort, cachePort);
        when(cachePort.getFraudScore(TOKEN)).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("should return score from Bedrock")
    void shouldReturnScoreFromBedrock() {
        when(fraudAnalysisPort.analyze(any())).thenReturn(FraudScore.of(25));
        assertThat(service.analyze(payload()).score()).isEqualTo(25);
    }

    @Test
    @DisplayName("should pass correct request with card token to Bedrock")
    void shouldPassCorrectRequestToBedrock() {
        when(fraudAnalysisPort.analyze(any())).thenReturn(FraudScore.of(10));
        service.analyze(payload());
        var captor = ArgumentCaptor.forClass(FraudCandidate.class);
        verify(fraudAnalysisPort).analyze(captor.capture());
        assertThat(captor.getValue().cardToken()).isEqualTo(TOKEN);
        assertThat(captor.getValue().amount()).isEqualByComparingTo("500.00");
        assertThat(captor.getValue().brand()).isEqualTo(Brand.VISA);
    }

    @Test
    @DisplayName("should update cache after Bedrock analysis")
    void shouldUpdateCacheWithScore() {
        FraudScore score = FraudScore.of(35);
        when(fraudAnalysisPort.analyze(any())).thenReturn(score);
        service.analyze(payload());
        verify(cachePort).putFraudScore(TOKEN, score);
    }

    @Test
    @DisplayName("should return cached score without calling Bedrock")
    void shouldReturnCachedScoreWithoutCallingBedrock() {
        when(cachePort.getFraudScore(TOKEN)).thenReturn(Optional.of(FraudScore.of(55)));
        assertThat(service.analyze(payload()).score()).isEqualTo(55);
        verify(fraudAnalysisPort, never()).analyze(any());
    }

    private SagaPayload payload() {
        return SagaPayloadFixture.withCardToken(TOKEN);
    }
}
