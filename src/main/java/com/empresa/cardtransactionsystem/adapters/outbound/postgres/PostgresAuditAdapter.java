package com.empresa.cardtransactionsystem.adapters.outbound.postgres;

import com.empresa.cardtransactionsystem.domain.model.CardToken;
import com.empresa.cardtransactionsystem.domain.model.SagaPayload;
import com.empresa.cardtransactionsystem.domain.model.TransactionHistory;
import com.empresa.cardtransactionsystem.domain.model.TransactionStatus;
import com.empresa.cardtransactionsystem.domain.model.TransactionSummary;
import com.empresa.cardtransactionsystem.domain.ports.output.AuditSearchPort;
import com.empresa.cardtransactionsystem.domain.ports.output.TransactionHistoryPort;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
@Profile("ledger-postgres")
public class PostgresAuditAdapter implements AuditSearchPort, TransactionHistoryPort {

    private final TransactionAuditJpaRepository repository;

    public PostgresAuditAdapter(TransactionAuditJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void index(SagaPayload payload) {
        repository.save(TransactionAuditEntity.from(payload));
    }

    @Override
    public List<TransactionSummary> findByCardToken(CardToken cardToken, int limit) {
        return repository.findByCardTokenOrderByCreatedAtDesc(cardToken.value(), PageRequest.of(0, limit))
                .stream()
                .map(TransactionAuditEntity::toSummary)
                .toList();
    }

    @Override
    public List<TransactionSummary> findByStatus(TransactionStatus status, LocalDate from, LocalDate to) {
        return repository.findByStatusAndCreatedAtBetweenOrderByCreatedAtDesc(
                        status.name(), from.atStartOfDay(), to.plusDays(1).atStartOfDay())
                .stream()
                .map(TransactionAuditEntity::toSummary)
                .toList();
    }

    @Override
    public TransactionHistory findByCardToken(CardToken cardToken) {
        List<TransactionAuditEntity> docs = repository.findByCardTokenOrderByCreatedAtDesc(
                cardToken.value(), PageRequest.of(0, 10));

        List<TransactionSummary> recent = docs.stream().map(TransactionAuditEntity::toSummary).toList();

        LocalDateTime since24h = LocalDateTime.now().minusHours(24);
        int velocityLast24h = (int) docs.stream().filter(d -> d.getCreatedAt().isAfter(since24h)).count();

        LocalDateTime since30Days = LocalDateTime.now().minusDays(30);
        BigDecimal totalLast30Days = docs.stream()
                .filter(d -> d.getCreatedAt().isAfter(since30Days))
                .map(TransactionAuditEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new TransactionHistory(recent, velocityLast24h, totalLast30Days);
    }
}
