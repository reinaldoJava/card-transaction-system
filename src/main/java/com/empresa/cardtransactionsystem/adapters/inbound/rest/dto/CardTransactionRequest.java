package com.empresa.cardtransactionsystem.adapters.inbound.rest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CardTransactionRequest(
        @NotBlank(message = "Transaction ID is required")
        String transactionId,

        @NotNull(message = "Transaction UUID is required")
        UUID uuidTransaction,

        @NotNull(message = "Card data is required")
        @Valid CardDataRequest cardDataRequest,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than 0.00")
        @DecimalMax(value = "1000000.00", message = "Amount cannot exceed 1,000,000.00")
        BigDecimal amount,

        @Min(value = 1, message = "Installments must be at least 1")
        @Max(value = 24, message = "Installments cannot exceed 24")
        int installments
) {}
