package com.twenty9ine.frauddetection.domain.valueobject;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.Map;

import static com.twenty9ine.frauddetection.domain.valueobject.TimeWindow.*;

@Builder
public record VelocityMetrics(Map<TimeWindow, Long> transactionCounts,
                              Map<TimeWindow, BigDecimal> totalAmounts,
                              Map<TimeWindow, Long> uniqueMerchants,
                              Map<TimeWindow, Long> uniqueLocations) {

    //TODO: Consider using a constant for empty metrics
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
                FIVE_MINUTES, 0L,
                ONE_HOUR, 0L,
                TWENTY_FOUR_HOURS, 0L
            ))
            .uniqueLocations(Map.of(
                FIVE_MINUTES, 0L,
                ONE_HOUR, 0L,
                TWENTY_FOUR_HOURS, 0L
            ))
            .build();
    }

    public long getTransactionCount(TimeWindow timeWindow) {
        return transactionCounts.getOrDefault(timeWindow, 0L);
    }

    public BigDecimal getTotalAmount(TimeWindow timeWindow) {
        return totalAmounts.getOrDefault(timeWindow, BigDecimal.ZERO);
    }

    public long getUniqueMerchants(TimeWindow timeWindow) {
        return uniqueMerchants.getOrDefault(timeWindow, 0L);
    }

    public long getUniqueLocations(TimeWindow timeWindow) {
        return uniqueLocations.getOrDefault(timeWindow, 0L);
    }
}