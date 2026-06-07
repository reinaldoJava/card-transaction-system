package com.empresa.cardtransactionsystem.adapters.outbound.lambda.function;

import com.empresa.cardtransactionsystem.adapters.outbound.lambda.JwtGenerator;
import com.empresa.cardtransactionsystem.adapters.outbound.lambda.dto.TokenExchangeRequest;
import com.empresa.cardtransactionsystem.adapters.outbound.lambda.dto.TokenExchangeResponse;

import java.util.function.Function;

public class TokenExchangeFunction {

    public static Function<TokenExchangeRequest, TokenExchangeResponse> create(JwtGenerator jwtGenerator) {
        return request -> new TokenExchangeResponse(jwtGenerator.generate(request.username()));
    }
}
