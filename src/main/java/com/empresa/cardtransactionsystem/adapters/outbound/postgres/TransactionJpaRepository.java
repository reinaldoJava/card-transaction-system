package com.empresa.cardtransactionsystem.adapters.outbound.postgres;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface TransactionJpaRepository extends JpaRepository<TransactionEntity, UUID> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE TransactionEntity t SET t.status = :status WHERE t.correlationId = :id")
    void updateStatus(@Param("id") UUID correlationId, @Param("status") String status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE TransactionEntity t SET t.status = :status, t.reason = :reason WHERE t.correlationId = :id")
    void updateStatusAndReason(@Param("id") UUID correlationId,
                               @Param("status") String status,
                               @Param("reason") String reason);
}
