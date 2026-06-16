package com.empresa.cardtransactionsystem.domain.model.auth;

public record OpaqueToken(String value) {

    public OpaqueToken {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Opaque token cannot be blank");
        }
    }
}
