package com.twenty9ine.frauddetection.domain.valueobject;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

@Builder
public record VelocityMetrics(
    Map<Duration, Long> transactionCounts,
    Map<Duration, BigDecimal> totalAmounts,
    Map<Duration, Integer> uniqueMerchants,
    Map<Duration, Long> uniqueLocations
) {
    public static final Duration FIVE_MINUTES = Duration.ofMinutes(5);
    public static final Duration ONE_HOUR = Duration.ofHours(1);
    public static final Duration TWENTY_FOUR_HOURS = Duration.ofHours(24);

    public static VelocityMetrics empty() {
        return VelocityMetrics.builder()
            .transactionCounts(Map.of(
                FIVE_MINUTES, 0L,
                ONE_HOUR, 0L,
                TWENTY_FOUR_HOURS, 0L
            ))
            .totalAmounts(Map.of(
                FIVE_MINUTES, BigDecimal.ZERO,
                ONE_HOUR, BigDecimal.ZERO,
                TWENTY_FOUR_HOURS, BigDecimal.ZERO
            ))
            .uniqueMerchants(Map.of(
                FIVE_MINUTES, 0,
                ONE_HOUR, 0,
                TWENTY_FOUR_HOURS, 0
            ))
            .uniqueLocations(Map.of(
                FIVE_MINUTES, 0L,
                ONE_HOUR, 0L,
                TWENTY_FOUR_HOURS, 0L
            ))
            .build();
    }

    public long getTransactionCount(Duration duration) {
        return transactionCounts.getOrDefault(duration, 0L);
    }

    public BigDecimal getTotalAmount(Duration duration) {
        return totalAmounts.getOrDefault(duration, BigDecimal.ZERO);
    }

    public int getUniqueMerchants(Duration duration) {
        return uniqueMerchants.getOrDefault(duration, 0);
    }

    public long getUniqueLocations(Duration duration) {
        return uniqueLocations.getOrDefault(duration, 0L);
    }
}