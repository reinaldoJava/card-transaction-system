package com.empresa.cardtransactionsystem.domain.ports.output;

import com.empresa.cardtransactionsystem.domain.model.CardToken;
import com.empresa.cardtransactionsystem.domain.model.SagaPayload;
import com.empresa.cardtransactionsystem.domain.model.TransactionStatus;
import com.empresa.cardtransactionsystem.domain.model.TransactionSummary;

import java.time.LocalDate;
import java.util.List;

public interface AuditSearchPort {
    void index(SagaPayload payload);
    List<TransactionSummary> findByCardToken(CardToken cardToken, int limit);
    List<TransactionSummary> findByStatus(TransactionStatus status, LocalDate from, LocalDate to);
}
