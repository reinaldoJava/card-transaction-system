package com.empresa.cardtransactionsystem.adapters.outbound.postgres;

import com.empresa.cardtransactionsystem.domain.model.SagaPayload;
import com.empresa.cardtransactionsystem.domain.model.TransactionStatus;
import com.empresa.cardtransactionsystem.domain.ports.output.TransactionRepositoryPort;
import jakarta.transaction.Transactional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("ledger-postgres")
public class PostgresTransactionAdapter implements TransactionRepositoryPort {

    private final TransactionJpaRepository repository;

    public PostgresTransactionAdapter(TransactionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void save(SagaPayload payload) {
        repository.save(TransactionEntity.from(payload));
    }

    @Override
    @Transactional
    public void updateStatus(UUID correlationId, TransactionStatus status) {
        repository.updateStatus(correlationId, status.name());
    }

    @Override
    public Optional<TransactionStatus> findStatus(UUID correlationId) {
        return repository.findById(correlationId)
                .map(e -> TransactionStatus.valueOf(e.getStatus()));
    }

    @Override
    public Optional<SagaPayload> findById(UUID correlationId) {
        return repository.findById(correlationId).map(TransactionEntity::toDomain);
    }
}
