package com.empresa.cardtransactionsystem.domain.model;

public record CardNumber(String value) {

    public CardNumber {
        if (value == null || !value.matches("\\d{16}")) {
            throw new IllegalArgumentException("Invalid card number: must contain exactly 16 digits");
        }
    }
}
