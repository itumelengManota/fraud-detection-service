package com.twenty9ine.frauddetection.domain.valueobject;

import com.twenty9ine.frauddetection.domain.valueobject.validation.ValidCountry;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Locale;

import static java.lang.Math.*;

public record Location(
    @NotNull Double latitude,
    @NotNull Double longitude,
    @ValidCountry
    String country,
    String city
) {
    private static final double EPSILON = 1e-9;

    public static Location of(double latitude, double longitude) {
        return new Location(latitude, longitude, null, null);
    }

    public static Location of(double latitude, double longitude, String country, String city) {
        return new Location(latitude, longitude, country, city);
    }

    public boolean isDomestic() {
        return Locale.getDefault().getCountry().equalsIgnoreCase(this.country);
    }

    public double distanceFrom(Location other) {
        double earthRadiusKm = 6371.0;

        double latitude1 = toRadians(this.latitude);
        double latitude2 = toRadians(other.latitude);

        double latitudeDistance = calculateDistance(latitude2, latitude1);
        double longitudeDistance = calculateDistance(toRadians(other.longitude), toRadians(this.longitude));

        double angularDistance = calculateAngularDistance(haversine(latitudeDistance, latitude1, latitude2, longitudeDistance));

        return earthRadiusKm * angularDistance;
    }

    private static double haversine(double latitudeDistance, double latitude1, double latitude2, double longitudeDistance) {
        return sin(latitudeDistance / 2) * sin(latitudeDistance / 2) +
                cos(latitude1) * cos(latitude2) *
                        sin(longitudeDistance / 2) * sin(longitudeDistance / 2);
    }

    private static double calculateAngularDistance(double a) {
        return 2 * atan2(sqrt(a), sqrt(1 - a));
    }

    private static double calculateDistance(double latitude2, double latitude1) {
        return latitude2 - latitude1;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Location other)) return false;
        return abs(this.latitude - other.latitude) < EPSILON &&
               abs(this.longitude - other.longitude) < EPSILON;
    }

    @Override
    public int hashCode() {
        long latBits = Double.doubleToLongBits(round(latitude / EPSILON) * EPSILON);
        long lonBits = Double.doubleToLongBits(round(longitude / EPSILON) * EPSILON);
        return 31 * Long.hashCode(latBits) + Long.hashCode(lonBits);
    }

    @Override
    public String toString() {
        return "Location[latitude=" + latitude + ", longitude=" + longitude + "]";
    }
}