package com.empresa.cardtransactionsystem.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GeoLocation(
        String code,
        String city,
        String country,
        double latitude,
        double longitude,
        @JsonProperty("risk_hint") String riskHint
) {
    public String display() {
        return "%s, %s (%.4f, %.4f)".formatted(city, country, latitude, longitude);
    }
}
