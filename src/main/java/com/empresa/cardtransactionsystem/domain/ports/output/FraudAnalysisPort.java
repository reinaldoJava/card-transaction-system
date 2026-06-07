package com.empresa.cardtransactionsystem.domain.ports.output;

import com.empresa.cardtransactionsystem.domain.model.FraudAnalysisRequest;
import com.empresa.cardtransactionsystem.domain.model.FraudScore;

public interface FraudAnalysisPort {
    FraudScore analyze(FraudAnalysisRequest request);
}
