package com.empresa.cardtransactionsystem.application.usecase;

import com.empresa.cardtransactionsystem.domain.model.TransactionStatus;
import com.empresa.cardtransactionsystem.domain.ports.input.CompensationUseCase;
import com.empresa.cardtransactionsystem.domain.ports.output.TransactionRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CompensationService implements CompensationUseCase {

    private final TransactionRepositoryPort repositoryPort;

    public CompensationService(TransactionRepositoryPort repositoryPort) {
        this.repositoryPort = repositoryPort;
    }

    @Override
    public void compensate(UUID uuidTransaction) {
        repositoryPort.updateStatus(uuidTransaction, TransactionStatus.REJECTED);
    }
}
