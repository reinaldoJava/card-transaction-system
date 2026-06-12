package com.empresa.cardtransactionsystem.domain.model;

import java.time.LocalTime;

public enum TimeOfDayRisk {

    DAWN("DAWN (00:00-06:00) — HIGH RISK"),
    EARLY_MORNING("EARLY_MORNING (06:00-09:00) — MODERATE RISK"),
    BUSINESS_HOURS("BUSINESS_HOURS (09:00-18:00) — LOW RISK"),
    EVENING("EVENING (18:00-22:00) — MODERATE RISK"),
    NIGHT("NIGHT (22:00-00:00) — ELEVATED RISK");

    private final String label;

    TimeOfDayRisk(String label) {
        this.label = label;
    }

    public static TimeOfDayRisk from(LocalTime time) {
        if (time.isBefore(LocalTime.of(6, 0)))   return DAWN;
        if (time.isBefore(LocalTime.of(9, 0)))   return EARLY_MORNING;
        if (time.isBefore(LocalTime.of(18, 0)))  return BUSINESS_HOURS;
        if (time.isBefore(LocalTime.of(22, 0)))  return EVENING;
        return NIGHT;
    }

    @Override
    public String toString() {
        return label;
    }
}
