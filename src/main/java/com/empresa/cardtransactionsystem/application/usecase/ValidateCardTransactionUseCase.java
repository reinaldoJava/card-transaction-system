package com.empresa.cardtransactionsystem.application.usecase;

import com.empresa.cardtransactionsystem.domain.model.SagaPayload;
import com.empresa.cardtransactionsystem.domain.model.TransactionStatus;
import com.empresa.cardtransactionsystem.domain.ports.input.ValidateTransactionUseCase;
import com.empresa.cardtransactionsystem.domain.ports.output.TransactionRepositoryPort;
import org.springframework.stereotype.Service;

@Service
public class ValidateCardTransactionUseCase implements ValidateTransactionUseCase {

    private final TransactionRepositoryPort repositoryPort;

    public ValidateCardTransactionUseCase(TransactionRepositoryPort repositoryPort) {
        this.repositoryPort = repositoryPort;
    }

    @Override
    public void validate(SagaPayload payload) {
        if (payload.status() != TransactionStatus.PENDING) {
            return;
        }
        repositoryPort.updateStatus(payload.correlationId(), TransactionStatus.APPROVED);
    }
}
