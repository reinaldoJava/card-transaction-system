package com.empresa.cardtransactionsystem.domain.ports.output;

import com.empresa.cardtransactionsystem.domain.model.FraudCandidate;
import com.empresa.cardtransactionsystem.domain.model.FraudScore;

public interface FraudAnalysisPort {
    FraudScore analyze(FraudCandidate request);
}
