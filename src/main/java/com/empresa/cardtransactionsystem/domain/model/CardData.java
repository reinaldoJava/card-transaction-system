package com.empresa.cardtransactionsystem.domain.model;

public record CardData(
        CardNumber cardNumber,
        Cvv cvv,
        String name,
        Brand brand
) {}
