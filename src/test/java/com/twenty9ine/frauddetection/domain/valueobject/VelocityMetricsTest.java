package com.twenty9ine.frauddetection.domain.valueobject;

    import org.junit.jupiter.api.Test;

    import java.math.BigDecimal;
    import java.time.Duration;
    import java.util.Map;

    import static org.assertj.core.api.Assertions.assertThat;

    class VelocityMetricsTest {

        @Test
        void testEmpty_ReturnsMetricsWithZeroValues() {
            VelocityMetrics metrics = VelocityMetrics.empty();

            assertThat(metrics).isNotNull();
            assertThat(metrics.getTransactionCount(VelocityMetrics.FIVE_MINUTES)).isZero();
            assertThat(metrics.getTransactionCount(VelocityMetrics.ONE_HOUR)).isZero();
            assertThat(metrics.getTransactionCount(VelocityMetrics.TWENTY_FOUR_HOURS)).isZero();
            assertThat(metrics.getTotalAmount(VelocityMetrics.FIVE_MINUTES)).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(metrics.getTotalAmount(VelocityMetrics.ONE_HOUR)).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(metrics.getTotalAmount(VelocityMetrics.TWENTY_FOUR_HOURS)).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(metrics.getUniqueMerchants(VelocityMetrics.FIVE_MINUTES)).isZero();
            assertThat(metrics.getUniqueMerchants(VelocityMetrics.ONE_HOUR)).isZero();
            assertThat(metrics.getUniqueMerchants(VelocityMetrics.TWENTY_FOUR_HOURS)).isZero();
            assertThat(metrics.getUniqueLocations(VelocityMetrics.FIVE_MINUTES)).isZero();
            assertThat(metrics.getUniqueLocations(VelocityMetrics.ONE_HOUR)).isZero();
            assertThat(metrics.getUniqueLocations(VelocityMetrics.TWENTY_FOUR_HOURS)).isZero();
        }

        @Test
        void testBuilder_CreatesMetricsWithProvidedValues() {
            VelocityMetrics metrics = VelocityMetrics.builder()
                .transactionCounts(Map.of(
                    VelocityMetrics.FIVE_MINUTES, 5L,
                    VelocityMetrics.ONE_HOUR, 20L,
                    VelocityMetrics.TWENTY_FOUR_HOURS, 100L
                ))
                .totalAmounts(Map.of(
                    VelocityMetrics.FIVE_MINUTES, new BigDecimal("500.00"),
                    VelocityMetrics.ONE_HOUR, new BigDecimal("2000.00"),
                    VelocityMetrics.TWENTY_FOUR_HOURS, new BigDecimal("10000.00")
                ))
                .uniqueMerchants(Map.of(
                    VelocityMetrics.FIVE_MINUTES, 3,
                    VelocityMetrics.ONE_HOUR, 10,
                    VelocityMetrics.TWENTY_FOUR_HOURS, 25
                ))
                .uniqueLocations(Map.of(
                    VelocityMetrics.FIVE_MINUTES, 2L,
                    VelocityMetrics.ONE_HOUR, 8L,
                    VelocityMetrics.TWENTY_FOUR_HOURS, 15L
                ))
                .build();

            assertThat(metrics.getTransactionCount(VelocityMetrics.FIVE_MINUTES)).isEqualTo(5L);
            assertThat(metrics.getTransactionCount(VelocityMetrics.ONE_HOUR)).isEqualTo(20L);
            assertThat(metrics.getTransactionCount(VelocityMetrics.TWENTY_FOUR_HOURS)).isEqualTo(100L);
            assertThat(metrics.getTotalAmount(VelocityMetrics.FIVE_MINUTES)).isEqualByComparingTo("500.00");
            assertThat(metrics.getTotalAmount(VelocityMetrics.ONE_HOUR)).isEqualByComparingTo("2000.00");
            assertThat(metrics.getTotalAmount(VelocityMetrics.TWENTY_FOUR_HOURS)).isEqualByComparingTo("10000.00");
            assertThat(metrics.getUniqueMerchants(VelocityMetrics.FIVE_MINUTES)).isEqualTo(3);
            assertThat(metrics.getUniqueMerchants(VelocityMetrics.ONE_HOUR)).isEqualTo(10);
            assertThat(metrics.getUniqueMerchants(VelocityMetrics.TWENTY_FOUR_HOURS)).isEqualTo(25);
            assertThat(metrics.getUniqueLocations(VelocityMetrics.FIVE_MINUTES)).isEqualTo(2L);
            assertThat(metrics.getUniqueLocations(VelocityMetrics.ONE_HOUR)).isEqualTo(8L);
            assertThat(metrics.getUniqueLocations(VelocityMetrics.TWENTY_FOUR_HOURS)).isEqualTo(15L);
        }

        @Test
        void testGetTransactionCount_UnknownDuration_ReturnsZero() {
            VelocityMetrics metrics = VelocityMetrics.empty();

            assertThat(metrics.getTransactionCount(Duration.ofMinutes(30))).isZero();
        }

        @Test
        void testGetTotalAmount_UnknownDuration_ReturnsZero() {
            VelocityMetrics metrics = VelocityMetrics.empty();

            assertThat(metrics.getTotalAmount(Duration.ofMinutes(30))).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void testGetUniqueMerchants_UnknownDuration_ReturnsZero() {
            VelocityMetrics metrics = VelocityMetrics.empty();

            assertThat(metrics.getUniqueMerchants(Duration.ofMinutes(30))).isZero();
        }

        @Test
        void testGetUniqueLocations_UnknownDuration_ReturnsZero() {
            VelocityMetrics metrics = VelocityMetrics.empty();

            assertThat(metrics.getUniqueLocations(Duration.ofMinutes(30))).isZero();
        }

        @Test
        void testRecord_EqualsAndHashCode() {
            VelocityMetrics metrics1 = VelocityMetrics.builder()
                .transactionCounts(Map.of(VelocityMetrics.FIVE_MINUTES, 5L))
                .totalAmounts(Map.of(VelocityMetrics.FIVE_MINUTES, new BigDecimal("100.00")))
                .uniqueMerchants(Map.of(VelocityMetrics.FIVE_MINUTES, 2))
                .uniqueLocations(Map.of(VelocityMetrics.FIVE_MINUTES, 1L))
                .build();

            VelocityMetrics metrics2 = VelocityMetrics.builder()
                .transactionCounts(Map.of(VelocityMetrics.FIVE_MINUTES, 5L))
                .totalAmounts(Map.of(VelocityMetrics.FIVE_MINUTES, new BigDecimal("100.00")))
                .uniqueMerchants(Map.of(VelocityMetrics.FIVE_MINUTES, 2))
                .uniqueLocations(Map.of(VelocityMetrics.FIVE_MINUTES, 1L))
                .build();

            assertThat(metrics1)
                .isEqualTo(metrics2)
                .hasSameHashCodeAs(metrics2);
        }

        @Test
        void testRecord_NotEquals() {
            VelocityMetrics metrics1 = VelocityMetrics.builder()
                .transactionCounts(Map.of(VelocityMetrics.FIVE_MINUTES, 5L))
                .totalAmounts(Map.of(VelocityMetrics.FIVE_MINUTES, new BigDecimal("100.00")))
                .uniqueMerchants(Map.of(VelocityMetrics.FIVE_MINUTES, 2))
                .uniqueLocations(Map.of(VelocityMetrics.FIVE_MINUTES, 1L))
                .build();

            VelocityMetrics metrics2 = VelocityMetrics.builder()
                .transactionCounts(Map.of(VelocityMetrics.FIVE_MINUTES, 10L))
                .totalAmounts(Map.of(VelocityMetrics.FIVE_MINUTES, new BigDecimal("100.00")))
                .uniqueMerchants(Map.of(VelocityMetrics.FIVE_MINUTES, 2))
                .uniqueLocations(Map.of(VelocityMetrics.FIVE_MINUTES, 1L))
                .build();

            assertThat(metrics1).isNotEqualTo(metrics2);
        }

        @Test
        void testDurationConstants() {
            assertThat(VelocityMetrics.FIVE_MINUTES).isEqualTo(Duration.ofMinutes(5));
            assertThat(VelocityMetrics.ONE_HOUR).isEqualTo(Duration.ofHours(1));
            assertThat(VelocityMetrics.TWENTY_FOUR_HOURS).isEqualTo(Duration.ofHours(24));
        }
    }