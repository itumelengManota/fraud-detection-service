package com.twenty9ine.frauddetection.domain.valueobject;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

import static java.lang.Math.*;

public record Location(
    @NotNull double latitude,
    @NotNull double longitude,
    String country,
    String city,
    @NotNull Instant timestamp
) {
    public double distanceFrom(Location other) {
        double earthRadiusKm = 6371.0;  // Radius of the Earth in kilometers

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

    private static double calculateDistance(double lat2, double lat1) {
        return lat2 - lat1;
    }
}
