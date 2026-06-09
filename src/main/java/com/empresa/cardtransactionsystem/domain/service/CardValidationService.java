package com.empresa.cardtransactionsystem.domain.service;

import com.empresa.cardtransactionsystem.domain.model.CardData;
import com.empresa.cardtransactionsystem.domain.model.CardToken;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public class CardValidationService {

    public CardToken tokenize(CardData cardData) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(cardData.cardNumber().value().getBytes(StandardCharsets.UTF_8));
            long msb = 0;
            long lsb = 0;
            for (int i = 0; i < 8; i++) msb = (msb << 8) | (hash[i] & 0xff);
            for (int i = 8; i < 16; i++) lsb = (lsb << 8) | (hash[i] & 0xff);
            return new CardToken(new UUID(msb, lsb).toString());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
