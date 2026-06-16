package com.empresa.cardtransactionsystem.domain.ports.input;

import com.empresa.cardtransactionsystem.domain.model.auth.JwtToken;

public interface LoginUseCase {
    JwtToken login(String username, String rawPassword);
}
