package com.empresa.cardtransactionsystem.adapters.inbound.rest.dto;

import com.empresa.cardtransactionsystem.domain.model.TransactionStatus;

import java.util.UUID;

public record TransactionStatusResponse(UUID correlationId, TransactionStatus status, String reason) {}
