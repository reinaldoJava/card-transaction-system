package com.empresa.cardtransactionsystem.adapters.outbound.postgres;

import com.empresa.cardtransactionsystem.domain.model.auth.User;
import com.empresa.cardtransactionsystem.domain.ports.output.UserRepositoryPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@Profile("ledger-postgres")
public class PostgresUserRepositoryAdapter implements UserRepositoryPort {

    private final UserJpaRepository repository;

    public PostgresUserRepositoryAdapter(UserJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return repository.findById(username).map(UserEntity::toDomain);
    }
}
