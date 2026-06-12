package com.empresa.cardtransactionsystem.domain.model;

import java.nio.charset.StandardCharsets;

public record CardData(
        CardNumber cardNumber,
        Cvv cvv,
        String name,
        Brand brand
) {
    public byte[] getNumberBytesForHashing() {
        return cardNumber.value().getBytes(StandardCharsets.UTF_8);
    }
}
