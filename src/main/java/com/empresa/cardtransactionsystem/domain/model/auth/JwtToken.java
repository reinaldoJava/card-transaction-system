package com.empresa.cardtransactionsystem.domain.model.auth;

public record JwtToken(String value) {

    public JwtToken {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("JWT token cannot be blank");
        }
    }
}
