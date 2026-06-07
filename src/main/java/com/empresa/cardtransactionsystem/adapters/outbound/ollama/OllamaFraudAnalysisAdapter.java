package com.empresa.cardtransactionsystem.adapters.outbound.ollama;

import com.empresa.cardtransactionsystem.domain.model.FraudAnalysisRequest;
import com.empresa.cardtransactionsystem.domain.model.FraudScore;
import com.empresa.cardtransactionsystem.domain.ports.output.FraudAnalysisPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class OllamaFraudAnalysisAdapter implements FraudAnalysisPort {

    private static final Pattern SCORE_PATTERN = Pattern.compile("(\\d+)");
    private static final String MODEL = "mistral";

    private final RestTemplate restTemplate;
    private final String ollamaUrl;

    public OllamaFraudAnalysisAdapter(
            RestTemplate restTemplate,
            @Value("${ollama.url:http://localhost:11434}") String ollamaUrl) {
        this.restTemplate = restTemplate;
        this.ollamaUrl = ollamaUrl;
    }

    @Override
    public FraudScore analyze(FraudAnalysisRequest request) {
        String prompt = buildPrompt(request);
        String response = callOllama(prompt);
        return FraudScore.of(extractScore(response));
    }

    private String callOllama(String prompt) {
        OllamaRequest ollamaRequest = new OllamaRequest(MODEL, prompt, false);
        OllamaResponse response = restTemplate.postForObject(
                ollamaUrl + "/api/generate",
                ollamaRequest,
                OllamaResponse.class
        );
        return response != null ? response.response() : "50";
    }

    private String buildPrompt(FraudAnalysisRequest request) {
        return """
                Analyze this credit card transaction for fraud risk and respond with ONLY a number 0-100.
                0 = no fraud risk, 100 = definite fraud.

                Transaction details:
                - Amount: %s
                - Installments: %d
                - Brand: %s

                Respond with only the number, nothing else."""
                .formatted(request.amount(), request.installments(), request.brand().name());
    }

    private int extractScore(String response) {
        Matcher matcher = SCORE_PATTERN.matcher(response);
        if (matcher.find()) {
            int score = Integer.parseInt(matcher.group(1));
            return Math.min(100, Math.max(0, score));
        }
        return 50;
    }

    record OllamaRequest(String model, String prompt, boolean stream) {}

    record OllamaResponse(String response) {}
}
