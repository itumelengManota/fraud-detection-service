package com.twenty9ine.frauddetection.domain.valueobject;

import lombok.Builder;

@Builder
public record GeographicContext(
        boolean isImpossibleTravel,
        double distanceKm,
        double travelSpeed,
        Location previousLocation,
        Location currentLocation
) {
    public static GeographicContext normal() {
        return GeographicContext.builder()
                .isImpossibleTravel(false)
                .distanceKm(0.0)
                .travelSpeed(0.0)
                .build();
    }
}
