package com.empresa.cardtransactionsystem.adapters.inbound.rest.dto;

public record FieldError(
        String field,
        String message,
        Object rejectedValue
) {}
