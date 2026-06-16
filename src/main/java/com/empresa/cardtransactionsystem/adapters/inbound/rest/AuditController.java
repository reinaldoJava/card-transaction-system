package com.empresa.cardtransactionsystem.adapters.inbound.rest;

import com.empresa.cardtransactionsystem.domain.model.CardToken;
import com.empresa.cardtransactionsystem.domain.model.TransactionStatus;
import com.empresa.cardtransactionsystem.domain.model.TransactionSummary;
import com.empresa.cardtransactionsystem.domain.ports.output.AuditSearchPort;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Consulta da auditoria de transações (projeção Kafka -> Postgres).
 * Disponível apenas onde a auditoria existe (profile ledger-postgres / local-rich).
 * Protegido por JWT via Spring Security (default-deny em SecurityConfig).
 */
@RestController
@RequestMapping("/audit")
@Profile("ledger-postgres")
public class AuditController {

    private final AuditSearchPort auditSearchPort;

    public AuditController(AuditSearchPort auditSearchPort) {
        this.auditSearchPort = auditSearchPort;
    }

    @GetMapping("/card/{cardToken}")
    public List<TransactionSummary> byCard(
            @PathVariable String cardToken,
            @RequestParam(defaultValue = "50") int limit) {
        return auditSearchPort.findByCardToken(new CardToken(cardToken), limit);
    }

    @GetMapping("/status")
    public List<TransactionSummary> byStatus(
            @RequestParam TransactionStatus status,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return auditSearchPort.findByStatus(status, from, to);
    }
}
