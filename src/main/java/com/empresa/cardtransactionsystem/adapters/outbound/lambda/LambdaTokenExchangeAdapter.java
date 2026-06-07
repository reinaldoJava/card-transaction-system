package com.empresa.cardtransactionsystem.adapters.outbound.lambda;

import com.empresa.cardtransactionsystem.adapters.outbound.lambda.dto.TokenExchangeRequest;
import com.empresa.cardtransactionsystem.adapters.outbound.lambda.dto.TokenExchangeResponse;
import com.empresa.cardtransactionsystem.domain.model.auth.JwtToken;
import com.empresa.cardtransactionsystem.domain.model.auth.OpaqueToken;
import com.empresa.cardtransactionsystem.domain.ports.output.TokenExchangePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvocationType;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import tools.jackson.databind.ObjectMapper;

@Component
public class LambdaTokenExchangeAdapter implements TokenExchangePort {

    private final LambdaClient lambdaClient;
    private final ObjectMapper objectMapper;
    private final String functionName;

    public LambdaTokenExchangeAdapter(
            LambdaClient lambdaClient,
            ObjectMapper objectMapper,
            @Value("${auth.lambda.function-name}") String functionName) {
        this.lambdaClient = lambdaClient;
        this.objectMapper = objectMapper;
        this.functionName = functionName;
    }

    @Override
    public JwtToken exchange(OpaqueToken opaqueToken, String username) {
        var payload = serialize(new TokenExchangeRequest(opaqueToken.value(), username));

        var request = InvokeRequest.builder()
                .functionName(functionName)
                .invocationType(InvocationType.REQUEST_RESPONSE)
                .payload(SdkBytes.fromUtf8String(payload))
                .build();

        var response = lambdaClient.invoke(request);
        var exchangeResponse = deserialize(response.payload().asUtf8String());

        return new JwtToken(exchangeResponse.jwt());
    }

    private String serialize(TokenExchangeRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize token exchange request", e);
        }
    }

    private TokenExchangeResponse deserialize(String json) {
        try {
            return objectMapper.readValue(json, TokenExchangeResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize token exchange response", e);
        }
    }
}
