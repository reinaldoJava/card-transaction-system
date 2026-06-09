package com.empresa.cardtransactionsystem.adapters.outbound.callback;

import com.empresa.cardtransactionsystem.domain.model.SagaPayload;
import com.empresa.cardtransactionsystem.domain.model.TransactionResult;
import com.empresa.cardtransactionsystem.domain.ports.output.CallbackNotifierPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;

@Component
public class HttpCallbackNotifierAdapter implements CallbackNotifierPort {

    private static final Logger log = LoggerFactory.getLogger(HttpCallbackNotifierAdapter.class);
    private static final String ALGORITHM = "HmacSHA256";

    private final ObjectMapper objectMapper;
    private final String secret;
    private final HttpClient httpClient;

    public HttpCallbackNotifierAdapter(
            ObjectMapper objectMapper,
            @Value("${callback.secret:}") String secret) {
        this.objectMapper = objectMapper;
        this.secret = secret;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public void notify(SagaPayload payload, TransactionResult result) {
        String callbackUrl = payload.callbackUrl();
        if (callbackUrl == null || callbackUrl.isBlank()) {
            return;
        }
        try {
            String body = objectMapper.writeValueAsString(result);
            String signature = hmac(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(callbackUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("X-Signature", "hmac-sha256=" + signature)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 300) {
                log.warn("Callback to {} returned HTTP {}", callbackUrl, response.statusCode());
            }
        } catch (Exception e) {
            log.error("Failed to deliver callback to {}: {}", callbackUrl, e.getMessage());
        }
    }

    private String hmac(String data) throws Exception {
        if (secret == null || secret.isBlank()) {
            return "";
        }
        Mac mac = Mac.getInstance(ALGORITHM);
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
        byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(bytes);
    }
}
