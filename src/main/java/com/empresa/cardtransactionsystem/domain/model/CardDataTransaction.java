package com.empresa.cardtransactionsystem.domain.model;

import java.util.UUID;

public record CardDataTransaction(
        String transactionId,
        UUID uuidTransaction,
        String card,
        CardData cardData){}
