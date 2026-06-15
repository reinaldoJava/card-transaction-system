package com.empresa.cardtransactionsystem.domain.ports.input;

import com.empresa.cardtransactionsystem.domain.model.FraudScore;
import com.empresa.cardtransactionsystem.domain.model.SagaPayload;

/**
 * Regras de degrade (rule-based fallback) usadas quando a análise de fraude por IA está indisponível.
 * Compartilhado pelos dois caminhos de saga (Temporal local e Step Functions AWS).
 */
public interface FraudFallbackUseCase {
    FraudScore evaluate(SagaPayload payload);
}
