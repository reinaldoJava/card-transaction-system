package com.empresa.cardtransactionsystem.domain.model;

public enum GeoRiskLevel {
    LOCAL,         // < 100 km   — mesmo estado/cidade
    DOMESTIC,      // 100–1000 km — viagem nacional plausível
    REGIONAL,      // 1000–4000 km — América do Sul
    INTERNATIONAL, // 4000–8000 km — EUA, Europa próxima
    EXTREME        // > 8000 km  — Polo Norte, Saara, Ásia, Oceania → bloquear no fallback
}
