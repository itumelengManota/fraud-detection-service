package com.twenty9ine.frauddetection.infrastructure.adapter.account.dto;

import com.twenty9ine.frauddetection.domain.valueobject.validation.ValidCountry;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LocationDto(@NotNull double latitude,
                          @NotNull double longitude,

                          @NotNull
                          @ValidCountry
                          String country,

                          @NotBlank
                          String city) { }
