package com.empresa.cardtransactionsystem.domain.model;

import java.math.BigDecimal;
import java.util.List;

public record TransactionHistory(
        List<TransactionSummary> recent,
        int velocityLast24h,
        BigDecimal totalAmountLast30Days
) {
    public static TransactionHistory empty() {
        return new TransactionHistory(List.of(), 0, BigDecimal.ZERO);
    }
}
