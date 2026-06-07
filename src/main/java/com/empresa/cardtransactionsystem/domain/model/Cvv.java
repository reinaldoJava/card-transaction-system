package com.empresa.cardtransactionsystem.domain.model;

public record Cvv(String value) {

    public Cvv {
        if (value == null || !value.matches("\\d{3,4}")) {
            throw new IllegalArgumentException("Invalid CVV: must contain 3 or 4 digits");
        }
    }
}
