package com.empresa.cardtransactionsystem.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

public record TransactionHistory(
        List<TransactionSummary> recent,
        int velocityLast24h,
        BigDecimal totalAmountLast30Days
) {
    public static TransactionHistory empty() {
        return new TransactionHistory(List.of(), 0, BigDecimal.ZERO);
    }

    public BigDecimal averageAmount() {
        if (recent.isEmpty()) return BigDecimal.ZERO;
        return recent.stream()
                .map(TransactionSummary::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(recent.size()), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal maxAmount() {
        return recent.stream()
                .map(TransactionSummary::amount)
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);
    }

    public int rejectionRatePct() {
        if (recent.isEmpty()) return 0;
        long rejectedCount = recent.stream()
                .filter(t -> t.status() == TransactionStatus.REJECTED)
                .count();
        return (int) (rejectedCount * 100L / recent.size());
    }

    public long burstCountLastHour() {
        LocalDateTime now = LocalDateTime.now();
        return recent.stream()
                .filter(t -> ChronoUnit.MINUTES.between(t.createdAt(), now) <= 60)
                .count();
    }

    public long minutesSinceLastTransaction() {
        LocalDateTime now = LocalDateTime.now();
        return recent.stream()
                .map(TransactionSummary::createdAt)
                .max(Comparator.naturalOrder())
                .map(last -> ChronoUnit.MINUTES.between(last, now))
                .orElse(-1L);
    }
}
