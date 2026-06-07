package com.empresa.cardtransactionsystem.application.usecase;

import com.empresa.cardtransactionsystem.domain.model.TransactionResult;
import com.empresa.cardtransactionsystem.domain.model.TransactionStatus;
import com.empresa.cardtransactionsystem.domain.ports.output.CachePort;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class IdempotencyService {

    private final CachePort cachePort;

    public IdempotencyService(CachePort cachePort) {
        this.cachePort = cachePort;
    }

    public Optional<TransactionResult> check(String transactionId) {
        return cachePort.getIdempotencyResult(transactionId);
    }

    public void store(String transactionId, TransactionResult result) {
        if (result.status() != TransactionStatus.TIMEOUT) {
            cachePort.putIdempotencyResult(transactionId, result);
        }
    }
}
