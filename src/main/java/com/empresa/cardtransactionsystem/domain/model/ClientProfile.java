package com.empresa.cardtransactionsystem.domain.model;

import java.math.BigDecimal;

public record ClientProfile(
        BigDecimal creditLimit,
        BigDecimal usedCredit,
        int maxInstallments,
        BigDecimal monthlyRate,
        boolean vip
) {
    public static final int DEFAULT_MAX_INSTALLMENTS = 24;
    public static final BigDecimal DEFAULT_MONTHLY_RATE = new BigDecimal("0.01");

    public BigDecimal availableCredit() {
        return creditLimit.subtract(usedCredit);
    }

    public boolean hasAvailableCredit(BigDecimal amount) {
        return availableCredit().compareTo(a