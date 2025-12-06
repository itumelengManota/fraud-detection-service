package com.twenty9ine.frauddetection.application.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record LocationDto(
    @NotNull double latitude,
    @NotNull double longitude,
    String country,
    String city,
    @NotNull Instant timestamp
) { }
