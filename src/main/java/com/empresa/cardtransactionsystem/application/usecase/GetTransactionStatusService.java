package com.empresa.cardtransactionsystem.application.usecase;

import com.empresa.cardtransactionsystem.domain.model.TransactionResult;
import com.empresa.cardtransactionsystem.domain.ports.input.GetTransactionStatusUseCase;
import com.empresa.cardtransactionsystem.domain.ports.output.TransactionRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class GetTransactionStatusService implements GetTransactionStatusUseCase {

    private final TransactionRepositoryPort transactionRepository;

    public GetTransactionStatusService(TransactionRepositoryPort transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Override
    public Optional<TransactionResult> getStatus(UUID correlationId) {
        return transactionRepository.findStatus(correlationId).map(status -> {
            String reason = transactionRepository.findReason(correlationId).orElse(null);
            return new TransactionResult(correlationId, status, reason);
        });
    }
}
