package com.empresa.cardtransactionsystem.domain.service;

import com.empresa.cardtransactionsystem.domain.model.CardData;
import com.empresa.cardtransactionsystem.domain.model.CardToken;

public class CardValidationService {

    public CardToken tokenize(CardData cardData) {
        return CardToken.of(cardData.cardNumber());
    }
}
