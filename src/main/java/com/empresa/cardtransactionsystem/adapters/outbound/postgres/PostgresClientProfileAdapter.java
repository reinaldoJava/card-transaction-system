package com.empresa.cardtransactionsystem.adapters.outbound.postgres;

import com.empresa.cardtransactionsystem.domain.model.CardToken;
import com.empresa.cardtransactionsystem.domain.model.ClientProfile;
import com.empresa.cardtransactionsystem.domain.ports.output.ClientProfilePort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@Profile("ledger-postgres")
public class PostgresClientProfileAdapter implements ClientProfilePort {

    private final ClientProfileJpaRepository repository;

    public PostgresClientProfileAdapter(ClientProfileJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<ClientProfile> findByCardToken(CardToken cardToken) {
        return repository.findById(cardToken.value()).map(ClientProfileEntity::toDomain);
    }
}
