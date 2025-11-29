package com.twenty9ine.frauddetection.domain.valueobject;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

import static com.twenty9ine.frauddetection.domain.valueobject.TimeWindow.FIVE_MINUTES;
import static com.twenty9ine.frauddetection.domain.valueobject.TimeWindow.ONE_HOUR;
import static com.twenty9ine.frauddetection.domain.valueobject.TimeWindow.TWENTY_FOUR_HOURS;
import static org.assertj.core.api.Assertions.assertThat;

class VelocityMetricsTest {

    @Test
    void testEmpty_ReturnsMetricsWithZeroValues() {
        VelocityMetrics metrics = VelocityMetrics.empty();

        assertThat(metrics).isNotNull();
        assertThat(metrics.getTransactionCount(FIVE_MINUTES)).isZero();
        assertThat(metrics.getTransactionCount(ONE_HOUR)).isZero();
        assertThat(metrics.getTransactionCount(TWENTY_FOUR_HOURS)).isZero();
        assertThat(metrics.getTotalAmount(FIVE_MINUTES)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(metrics.getTotalAmount(ONE_HOUR)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(metrics.getTotalAmount(TWENTY_FOUR_HOURS)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(metrics.getUniqueMerchants(FIVE_MINUTES)).isZero();
        assertThat(metrics.getUniqueMerchants(ONE_HOUR)).isZero();
        assertThat(metrics.getUniqueMerchants(TWENTY_FOUR_HOURS)).isZero();
        assertThat(metrics.getUniqueLocations(FIVE_MINUTES)).isZero();
        assertThat(metrics.getUniqueLocations(ONE_HOUR)).isZero();
        assertThat(metrics.getUniqueLocations(TWENTY_FOUR_HOURS)).isZero();
    }

    @Test
    void testBuilder_CreatesMetricsWithProvidedValues() {
        VelocityMetrics metrics = VelocityMetrics.builder()
                .transactionCounts(Map.of(
                        FIVE_MINUTES, 5L,
                        ONE_HOUR, 20L,
                        TWENTY_FOUR_HOURS, 100L
                ))
                .totalAmounts(Map.of(
                        FIVE_MINUTES, new BigDecimal("500.00"),
                        ONE_HOUR, new BigDecimal("2000.00"),
                        TWENTY_FOUR_HOURS, new BigDecimal("10000.00")
                ))
                .uniqueMerchants(Map.of(
                        FIVE_MINUTES, 3L,
                        ONE_HOUR, 10L,
                        TWENTY_FOUR_HOURS, 25L
                ))
                .uniqueLocations(Map.of(
                        FIVE_MINUTES, 2L,
                        ONE_HOUR, 8L,
                        TWENTY_FOUR_HOURS, 15L
                ))
                .build();

        assertThat(metrics.getTransactionCount(FIVE_MINUTES)).isEqualTo(5L);
        assertThat(metrics.getTransactionCount(ONE_HOUR)).isEqualTo(20L);
        assertThat(metrics.getTransactionCount(TWENTY_FOUR_HOURS)).isEqualTo(100L);
        assertThat(metrics.getTotalAmount(FIVE_MINUTES)).isEqualByComparingTo("500.00");
        assertThat(metrics.getTotalAmount(ONE_HOUR)).isEqualByComparingTo("2000.00");
        assertThat(metrics.getTotalAmount(TWENTY_FOUR_HOURS)).isEqualByComparingTo("10000.00");
        assertThat(metrics.getUniqueMerchants(FIVE_MINUTES)).isEqualTo(3);
        assertThat(metrics.getUniqueMerchants(ONE_HOUR)).isEqualTo(10);
        assertThat(metrics.getUniqueMerchants(TWENTY_FOUR_HOURS)).isEqualTo(25);
        assertThat(metrics.getUniqueLocations(FIVE_MINUTES)).isEqualTo(2L);
        assertThat(metrics.getUniqueLocations(ONE_HOUR)).isEqualTo(8L);
        assertThat(metrics.getUniqueLocations(TWENTY_FOUR_HOURS)).isEqualTo(15L);
    }

    @Test
    void testRecord_EqualsAndHashCode() {
        VelocityMetrics metrics1 = VelocityMetrics.builder()
                .transactionCounts(Map.of(FIVE_MINUTES, 5L))
                .totalAmounts(Map.of(FIVE_MINUTES, new BigDecimal("100.00")))
                .uniqueMerchants(Map.of(FIVE_MINUTES, 2L))
                .uniqueLocations(Map.of(FIVE_MINUTES, 1L))
                .build();

        VelocityMetrics metrics2 = VelocityMetrics.builder()
                .transactionCounts(Map.of(FIVE_MINUTES, 5L))
                .totalAmounts(Map.of(FIVE_MINUTES, new BigDecimal("100.00")))
                .uniqueMerchants(Map.of(FIVE_MINUTES, 2L))
                .uniqueLocations(Map.of(FIVE_MINUTES, 1L))
                .build();

        assertThat(metrics1)
                .isEqualTo(metrics2)
                .hasSameHashCodeAs(metrics2);
    }

    @Test
    void testRecord_NotEquals() {
        VelocityMetrics metrics1 = VelocityMetrics.builder()
                .transactionCounts(Map.of(FIVE_MINUTES, 5L))
                .totalAmounts(Map.of(FIVE_MINUTES, new BigDecimal("100.00")))
                .uniqueMerchants(Map.of(FIVE_MINUTES, 2L))
                .uniqueLocations(Map.of(FIVE_MINUTES, 1L))
                .build();

        VelocityMetrics metrics2 = VelocityMetrics.builder()
                .transactionCounts(Map.of(FIVE_MINUTES, 10L))
                .totalAmounts(Map.of(FIVE_MINUTES, new BigDecimal("100.00")))
                .uniqueMerchants(Map.of(FIVE_MINUTES, 2L))
                .uniqueLocations(Map.of(FIVE_MINUTES, 1L))
                .build();

        assertThat(metrics1).isNotEqualTo(metrics2);
    }

    @Test
    void testDurationConstants() {
        assertThat(FIVE_MINUTES.getDuration()).isEqualTo(Duration.ofMinutes(5));
        assertThat(ONE_HOUR.getDuration()).isEqualTo(Duration.ofHours(1));
        assertThat(TWENTY_FOUR_HOURS.getDuration()).isEqualTo(Duration.ofHours(24));
    }
}