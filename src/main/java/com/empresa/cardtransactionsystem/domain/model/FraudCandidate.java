package com.empresa.cardtransactionsystem.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record FraudCandidate(
        CardToken cardToken,
        BigDecimal amount,
        int installments,
        Brand brand,
        String locationCode
) {
    public static FraudCandidate from(SagaPayload payload) {
        return new FraudCandidate(
                payload.cardToken(),
                payload.amount(),
                payload.installments(),
                payload.brand(),
                payload.locationCode()
        );
    }

    public String amountBracket() {
        if (amount.compareTo(new BigDecimal("100")) <= 0)       return "LOW (<=R$100)";
        if (amount.compareTo(new BigDecimal("500")) <= 0)       return "MEDIUM (R$100-500)";
        if (amount.compareTo(new BigDecimal("2000")) <= 0)      return "HIGH (R$500-2000)";
        return "VERY_HIGH (>R$2000)";
    }

    public boolean isRoundAmount() {
        return amount.remainder(new BigDecimal("100")).compareTo(BigDecimal.ZERO) == 0;
    }

    public String installmentRisk() {
        if (installments > 6 && amount.compareTo(new BigDecimal("500")) > 0)
            return "HIGH — many installments on high-value tx";
        if (installments > 3)
            return "ELEVATED — multiple installments";
        return "NORMAL";
    }

    public BigDecimal amountPerInstallment() {
        if (installments <= 1) return amount;
        return amount.divide(BigDecimal.valueOf(installments), 2, RoundingMode.HALF_UP);
    }
}
