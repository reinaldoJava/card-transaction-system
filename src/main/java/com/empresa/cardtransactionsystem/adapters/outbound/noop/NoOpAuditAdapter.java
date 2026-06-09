package com.empresa.cardtransactionsystem.adapters.outbound.noop;

import com.empresa.cardtransactionsystem.domain.model.CardToken;
import com.empresa.cardtransactionsystem.domain.model.SagaPayload;
import com.empresa.cardtransactionsystem.domain.model.TransactionStatus;
import com.empresa.cardtransactionsystem.domain.model.TransactionSummary;
import com.empresa.cardtransactionsystem.domain.ports.output.AuditSearchPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
@Profile("queue-none")
public class NoOpAuditAdapter implements AuditSearchPort {

    @Override
    public void index(SagaPayload payload) {}

    @Override
    public List<TransactionSummary> findByCardToken(CardToken cardToken, int limit) {
        return List.of();
    }

    @Override
    public List<TransactionSummary> findByStatus(TransactionStatus status, LocalDate from, LocalDate to) {
        return List.of();
    }
}
