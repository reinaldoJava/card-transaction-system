package com.empresa.cardtransactionsystem.adapters.outbound.lambda.dto;

public record TokenExchangeRequest(String opaqueToken, String username) {}
