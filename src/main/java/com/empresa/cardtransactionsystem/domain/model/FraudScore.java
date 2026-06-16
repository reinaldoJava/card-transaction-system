package com.empresa.cardtransactionsystem.domain.model;

public record FraudScore(int score) {

    public FraudScore {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("FraudScore must be between 0 and 100");
        }
    }

    public boolean exceedsThreshold(int threshold) {
        return score >= threshold;
    }

    public static FraudScore of(int score) {
        return new FraudScore(score);
    }
}
