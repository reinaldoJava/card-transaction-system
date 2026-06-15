package com.empresa.cardtransactionsystem.adapters.outbound.callback;

import com.empresa.cardtransactionsystem.domain.model.SagaPayload;
import com.empresa.cardtransactionsystem.domain.model.TransactionResult;
import com.empresa.cardtransactionsystem.domain.ports.output.CallbackNotifierPort;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.Callable;

@Component
public class HttpCallbackNotifierAdapter implements CallbackNotifierPort {

    private static final Logger log = LoggerFactory.getLogger(HttpCallbackNotifierAdapter.class);
    private static final String ALGORITHM = "HmacSHA256";

    private final ObjectMapper objectMapper;
    private final String secret;
    private final HttpClient httpClient;
    private final Retry retry;
    private final Counter deliveredCounter;
    private final Counter failedCounter;

    public HttpCallbackNotifierAdapter(
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            @Value("${callback.secret:}") String secret) {
        this.objectMapper = objectMapper;
        this.secret = secret;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        // 3 tentativas, backoff exponencial 1s -> 2s
        this.retry = Retry.of("webhookCallback", RetryConfig.custom()
                .maxAttempts(3)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(Duration.ofSeconds(1), 2.0))
                .retryExceptions(Exception.class)
                .build());
        this.deliveredCounter = Counter.builder("webhook.delivery").tag("result", "delivered")
                .description("Webhooks entregues com sucesso").register(meterRegistry);
        this.failedCounter = Counter.builder("webhook.delivery").tag("result", "failed")
                .description("Webhooks que falharam após todas as tentativas").register(meterRegistry);
    }

    @Override
    public void notify(SagaPayload payload, TransactionResult result) {
        String callbackUrl = payload.callbackUrl();
        if (callbackUrl == null || callbackUrl.isBlank()) {
            return;
        }
        String body;
        String signature;
        try {
            body = objectMapper.writeValueAsString(result);
            signature = hmac(body);
        } catch (Exception e) {
            log.error("Failed to build callback payload for {}: {}", callbackUrl, e.getMessage());
            failedCounter.increment();
            return;
        }

        Callable<Integer> sendOnce = () -> {
            int status = send(callbackUrl, body, signature);
            if (status >= 300) {
                throw new IOException("HTTP " + status);
            }
            return status;
        };

        try {
            retry.executeCallable(sendOnce);
            deliveredCounter.increment();
        } catch (Exception e) {
            log.error("Callback to {} failed after {} attempts: {} — cliente pode consultar via GET /status",
                    callbackUrl, retry.getRetryConfig().getMaxAttempts(), e.getMessage());
            failedCounter.increment();
        }
    }

    private int send(String callbackUrl, String body, String signature) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(callbackUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("X-Signature", "hmac-sha256=" + signature)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        return response.statusCode();
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
