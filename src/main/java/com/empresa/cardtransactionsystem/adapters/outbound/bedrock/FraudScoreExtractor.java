package com.empresa.cardtransactionsystem.adapters.outbound.bedrock;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FraudScoreExtractor {

    private static final Pattern SCORE_PATTERN = Pattern.compile("\"fraud_score\"\\s*:\\s*(\\d+)");

    public int extract(String text) {
        Matcher matcher = SCORE_PATTERN.matcher(text);
        if (matcher.find()) {
            int score = Integer.parseInt(matcher.group(1));
            return Math.clamp(score, 0, 100);
        }
        throw new IllegalStateException("Could not extract fraud_score from: " + text);
    }
}
