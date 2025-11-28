package com.twenty9ine.frauddetection.application.dto;

import com.twenty9ine.frauddetection.domain.valueobject.Location;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record LocationDto(
    @NotNull double latitude,
    @NotNull double longitude,
    String country,
    String city,
    @NotNull Instant timestamp
) {
    public Location toDomain() {
        return new Location(latitude, longitude, country, city, timestamp);
    }
}
