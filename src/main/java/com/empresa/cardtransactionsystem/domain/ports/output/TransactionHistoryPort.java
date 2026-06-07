package com.empresa.cardtransactionsystem.domain.ports.output;

import com.empresa.cardtransactionsystem.domain.model.CardToken;
import com.empresa.cardtransactionsystem.domain.model.TransactionHistory;

public interface TransactionHistoryPort {
    TransactionHistory findByCardToken(CardToken cardToken);
}
