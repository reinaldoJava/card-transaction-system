package com.empresa.cardtransactionsystem.domain.model;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public record CardToken(String value) {

    public CardToken {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("CardToken cannot be blank");
        }
    }

    public static CardToken of(CardNumber cardNumber) {
        return new CardToken(UUID.nameUUIDFromBytes(
                cardNumber.value().getBytes(StandardCharsets.UTF_8)).toString());
    }
}
