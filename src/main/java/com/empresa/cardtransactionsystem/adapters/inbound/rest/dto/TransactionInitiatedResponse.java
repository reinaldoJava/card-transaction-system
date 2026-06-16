package com.empresa.cardtransactionsystem.adapters.inbound.rest.dto;

import java.util.UUID;

public record TransactionInitiatedResponse(String transactionId, UUID correlationId) {}
