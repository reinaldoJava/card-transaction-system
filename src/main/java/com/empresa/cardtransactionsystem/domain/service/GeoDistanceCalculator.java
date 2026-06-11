package com.empresa.cardtransactionsystem.domain.service;

import com.empresa.cardtransactionsystem.domain.model.GeoLocation;
import com.empresa.cardtransactionsystem.domain.model.GeoRiskLevel;

public final class GeoDistanceCalculator {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private GeoDistanceCalculator() {}

    public static double distanceKm(GeoLocation a, GeoLocation b) {
        double lat1 = Math.toRadians(a.latitude());
        double lat2 = Math.toRadians(b.latitude());
        double dLat = Math.toRadians(b.latitude() - a.latitude());
        double dLon = Math.toRadians(b.longitude() - a.longitude());

        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }

    public static GeoRiskLevel riskLevel(double distanceKm) {
        if (distanceKm < 100)   return GeoRiskLevel.LOCAL;
        if (distanceKm < 1000)  return GeoRiskLevel.DOMESTIC;
        if (distanceKm < 4000)  return GeoRiskLevel.REGIONAL;
        if (distanceKm < 8000)  return GeoRiskLevel.INTERNATIONAL;
        return GeoRiskLevel.EXTREME;
    }

    public static GeoRiskLevel riskLevel(GeoLocation from, GeoLocation to) {
        return riskLevel(distanceKm(from, to));
    }
}
