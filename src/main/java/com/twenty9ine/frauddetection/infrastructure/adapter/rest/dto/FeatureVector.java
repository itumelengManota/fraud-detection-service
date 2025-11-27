package com.twenty9ine.frauddetection.infrastructure.adapter.rest.dto;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record FeatureVector(
    BigDecimal amount,
    String merchantCategory,
    int accountAge
) {}
