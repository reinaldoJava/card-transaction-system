package com.empresa.cardtransactionsystem.domain.model;

import java.math.BigDecimal;

public record MerchantProfile(Brand brand, BigDecimal riskMultiplier) {

    public static MerchantProfile forBrand(Brand brand) {
        BigDecimal risk = switch (brand) {
            case VISA, MASTER -> new BigDecimal("1.0");
            case AMEX -> new BigDecimal("1.3");
        };
        return new MerchantProfile(brand, risk);
    }
}
