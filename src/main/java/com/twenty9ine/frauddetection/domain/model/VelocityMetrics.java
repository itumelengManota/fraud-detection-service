package com.twenty9ine.frauddetection.domain.model;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record VelocityMetrics(
    long fiveMinuteCount,
    long oneHourCount,
    long twentyFourHourCount,
    BigDecimal totalAmount,
    int uniqueMerchants,
    long uniqueLocations
) {
    public static VelocityMetrics empty() {
        return VelocityMetrics.builder()
            .fiveMinuteCount(0)
            .oneHourCount(0)
            .twentyFourHourCount(0)
            .totalAmount(BigDecimal.ZERO)
            .uniqueMerchants(0)
            .uniqueLocations(0)
            .build();
    }
}
