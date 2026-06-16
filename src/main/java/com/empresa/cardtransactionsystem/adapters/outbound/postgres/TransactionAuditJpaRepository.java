package com.empresa.cardtransactionsystem.adapters.outbound.postgres;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface TransactionAuditJpaRepository extends JpaRepository<TransactionAuditEntity, UUID> {

    List<TransactionAuditEntity> findByCardTokenOrderByCreatedAtDesc(String cardToken, Pageable pageable);

    List<TransactionAuditEntity> findByStatusAndCreatedAtBetweenOrderByCreatedAtDesc(
            String status, LocalDateTime from, LocalDateTime to);
}
