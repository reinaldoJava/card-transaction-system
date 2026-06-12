package com.empresa.cardtransactionsystem.application.usecase;

import com.empresa.cardtransactionsystem.domain.model.FraudCandidate;
import com.empresa.cardtransactionsystem.domain.model.FraudScore;
import com.empresa.cardtransactionsystem.domain.model.SagaPayload;
import com.empresa.cardtransactionsystem.domain.ports.input.AnalyzeFraudUseCase;
import com.empresa.cardtransactionsystem.domain.ports.output.CachePort;
import com.empresa.cardtransactionsystem.domain.ports.output.FraudAnalysisPort;
import org.springframework.stereotype.Service;

@Service
public class FraudAnalysisService implements AnalyzeFraudUseCase {

    private final FraudAnalysisPort fraudAnalysisPort;
    private final CachePort cachePort;

    public FraudAnalysisService(FraudAnalysisPort fraudAnalysisPort, CachePort cachePort) {
        this.fraudAnalysisPort = fraudAnalysisPort;
        this.cachePort = cachePort;
    }

    @Override
    public FraudScore analyze(SagaPayload payload) {
        return cachePort.getFraudScore(payload.cardToken()).orElseGet(() -> {
            FraudScore score = fraudAnalysisPort.analyze(FraudCandidate.from(payload));
            cachePort.putFraudScore(payload.cardToken(), score);
            return score;
        });
    }
}
