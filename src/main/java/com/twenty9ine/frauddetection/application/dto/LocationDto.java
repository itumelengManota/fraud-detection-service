package com.twenty9ine.frauddetection.application.dto;

import com.twenty9ine.frauddetection.domain.valueobject.validation.ValidCountry;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LocationDto(
    @NotNull Double latitude,
    @NotNull Double longitude,

    @NotNull
    @ValidCountry
    String country,

    @NotBlank
    String city
) { }
