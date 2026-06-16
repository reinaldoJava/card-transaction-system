package com.empresa.cardtransactionsystem.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransactionSummary(
        UUID uuidTransaction,
        BigDecimal amount,
        TransactionStatus status,
        LocalDateTime createdAt
) {}
