package com.empresa.cardtransactionsystem.domain.model;

public record ValidationResult(boolean approved, String reason) {

    public static ValidationResult valid() {
        return new ValidationResult(true, null);
    }

    public static ValidationResult invalid(String reason) {
        return new ValidationResult(false, reason);
    }
}
