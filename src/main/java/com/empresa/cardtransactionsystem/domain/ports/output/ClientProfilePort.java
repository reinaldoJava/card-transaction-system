package com.empresa.cardtransactionsystem.domain.ports.output;

import com.empresa.cardtransactionsystem.domain.model.CardToken;
import com.empresa.cardtransactionsystem.domain.model.ClientProfile;

import java.util.Optional;

public interface ClientProfilePort {
    Optional<ClientProfile> findByCardToken(CardToken cardToken);
}
