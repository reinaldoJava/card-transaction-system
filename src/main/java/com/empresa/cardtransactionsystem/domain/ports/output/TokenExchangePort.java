package com.empresa.cardtransactionsystem.domain.ports.output;

import com.empresa.cardtransactionsystem.domain.model.auth.JwtToken;
import com.empresa.cardtransactionsystem.domain.model.auth.OpaqueToken;

public interface TokenExchangePort {
    JwtToken exchange(OpaqueToken opaqueToken, String username);
}
