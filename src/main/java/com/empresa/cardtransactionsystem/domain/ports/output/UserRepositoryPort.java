package com.empresa.cardtransactionsystem.domain.ports.output;

import com.empresa.cardtransactionsystem.domain.model.auth.User;

import java.util.Optional;

public interface UserRepositoryPort {
    Optional<User> findByUsername(String username);
}
