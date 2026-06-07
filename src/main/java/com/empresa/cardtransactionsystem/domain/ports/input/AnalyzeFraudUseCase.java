package com.empresa.cardtransactionsystem.domain.ports.input;

import com.empresa.cardtransactionsystem.domain.model.FraudScore;
import com.empresa.cardtransactionsystem.domain.model.SagaPayload;

public interface AnalyzeFraudUseCase {
    FraudScore analyze(SagaPayload payload);
}
