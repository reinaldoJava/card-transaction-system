package com.empresa.cardtransactionsystem.domain.ports.output;

import com.empresa.cardtransactionsystem.domain.model.CardToken;
import com.empresa.cardtransactionsystem.domain.model.ClientProfile;
import com.empresa.cardtransactionsystem.domain.model.FraudScore;
import com.empresa.cardtransactionsystem.domain.model.TransactionResult;

import java.util.Optional;

public interface CachePort {

    Optional<FraudScore> getFraudScore(CardToken cardToken);
    void putFraudScore(CardToken cardToken, FraudScore score);

    Optional<TransactionResult> getIdempotencyResult(String transactionId);
    void putIdempotencyResult(String transactionId, TransactionResult result);

    Optional<ClientProfile> getClientProfile(CardToken cardToken);
    void putClientProfile(CardToken cardToken, ClientProfile profile);
}
