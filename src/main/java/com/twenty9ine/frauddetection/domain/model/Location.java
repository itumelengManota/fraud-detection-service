package com.twenty9ine.frauddetection.domain.model;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record Location(
    @NotNull double latitude,
    @NotNull double longitude,
    String country,
    String city,
    @NotNull Instant timestamp
) {
    public double distanceFrom(Location other) {
        double earthRadiusKm = 6371.0;
        double lat1 = Math.toRadians(this.latitude);
        double lat2 = Math.toRadians(other.latitude);
        double lon1 = Math.toRadians(this.longitude);
        double lon2 = Math.toRadians(other.longitude);

        double dLat = lat2 - lat1;
        double dLon = lon2 - lon1;

        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                   Math.cos(lat1) * Math.cos(lat2) *
                   Math.sin(dLon/2) * Math.sin(dLon/2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));

        return earthRadiusKm * c;
    }
}
