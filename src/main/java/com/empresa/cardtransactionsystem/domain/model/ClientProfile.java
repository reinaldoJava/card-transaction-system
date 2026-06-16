package com.empresa.cardtransactionsystem.domain.model;

import java.math.BigDecimal;

public record ClientProfile(
        BigDecimal creditLimit,
        BigDecimal usedCredit,
        int maxInstallments,
        BigDecimal monthlyRate,
        boolean vip,
        String homeLocationCode
) {
    public BigDecimal availableCredit() {
        return creditLimit.subtract(usedCredit);
    }

    public boolean hasAvailableCredit(BigDecimal amount) {
        return availableCredit().compareTo(amount) >= 0;
    }
}
