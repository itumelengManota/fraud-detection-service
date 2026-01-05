package com.twenty9ine.frauddetection.infrastructure.adapter.cache;

import com.twenty9ine.frauddetection.application.port.out.VelocityServicePort;
import com.twenty9ine.frauddetection.domain.valueobject.*;
import com.twenty9ine.frauddetection.infrastructure.config.RedisConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

import static org.assertj.core.api.Assertions.*;

import static com.twenty9ine.frauddetection.domain.valueobject.TimeWindow.*;

@DataRedisTest
@Testcontainers
@Import({RedisConfig.class, VelocityCounterAdapter.class})
@Execution(ExecutionMode.CONCURRENT)
@ResourceLock("redis")
class VelocityCounterAdapterIntegrationTest {

    @Container
    private static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withReuse(true);

    @DynamicPropertySource
    static void configureRedis(DynamicPropertyRegistry registry) {
        redis.start();

        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private VelocityServicePort velocityService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    void setUp() {
        cleanupRedis();
    }

    private void cleanupRedis() {
        Assertions.assertNotNull(redisTemplate.getConnectionFactory());
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    // Generate unique identifiers per test
    private String uniqueAccountId(String base) {
        return base + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    @DisplayName("Should return empty metrics when no data exists in Redis")
    void shouldReturnEmptyMetricsWhenNoDataExists() {
        // Arrange
        String accountId = uniqueAccountId("ACC-001");
        Transaction transaction = createTestTransaction(accountId, "MERCH-001");

        // Act
        VelocityMetrics metrics = velocityService.findVelocityMetricsByTransaction(transaction);

        // Assert
        assertThat(metrics).isNotNull();
        assertThat(metrics.getTransactionCount(FIVE_MINUTES)).isZero();
        assertThat(metrics.getTransactionCount(ONE_HOUR)).isZero();
        assertThat(metrics.getTransactionCount(TWENTY_FOUR_HOURS)).isZero();
        assertThat(metrics.getTotalAmount(FIVE_MINUTES)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(metrics.getUniqueMerchants(FIVE_MINUTES)).isZero();
        assertThat(metrics.getUniqueLocations(FIVE_MINUTES)).isZero();
    }

    @Test
    @DisplayName("Should increment all counters correctly")
    void shouldIncrementAllCountersCorrectly() {
        // Arrange
        String accountId = uniqueAccountId("ACC-002");
        Transaction transaction = createTestTransaction(accountId, "MERCH-001");

        // Act
        velocityService.incrementCounters(transaction);

        // Assert
        VelocityMetrics metrics = velocityService.findVelocityMetricsByTransaction(transaction);
        assertThat(metrics.getTransactionCount(FIVE_MINUTES)).isEqualTo(1L);
        assertThat(metrics.getTransactionCount(ONE_HOUR)).isEqualTo(1L);
        assertThat(metrics.getTransactionCount(TWENTY_FOUR_HOURS)).isEqualTo(1L);
        assertThat(metrics.getTotalAmount(FIVE_MINUTES)).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(metrics.getUniqueMerchants(FIVE_MINUTES)).isEqualTo(1L);
        assertThat(metrics.getUniqueLocations(FIVE_MINUTES)).isEqualTo(1L);
    }

    @Test
    @DisplayName("Should track multiple transactions correctly")
    void shouldTrackMultipleTransactionsCorrectly() {
        // Arrange
        String accountId = uniqueAccountId("ACC-003");
        Transaction transaction1 = createTestTransaction(accountId, "MERCH-001");
        Transaction transaction2 = createTestTransaction(accountId, "MERCH-002");
        Transaction transaction3 = createTestTransaction(accountId, "MERCH-003");

        // Act
        velocityService.incrementCounters(transaction1);
        velocityService.incrementCounters(transaction2);
        velocityService.incrementCounters(transaction3);

        // Assert
        VelocityMetrics metrics = velocityService.findVelocityMetricsByTransaction(transaction3);
        assertThat(metrics.getTransactionCount(FIVE_MINUTES)).isEqualTo(3L);
        assertThat(metrics.getTransactionCount(ONE_HOUR)).isEqualTo(3L);
        assertThat(metrics.getTransactionCount(TWENTY_FOUR_HOURS)).isEqualTo(3L);
        assertThat(metrics.getTotalAmount(FIVE_MINUTES)).isEqualByComparingTo(new BigDecimal("300.00"));
        assertThat(metrics.getUniqueMerchants(FIVE_MINUTES)).isEqualTo(3L);
    }

    @Test
    @DisplayName("Should track unique merchants using HyperLogLog")
    void shouldTrackUniqueMerchantsUsingHyperLogLog() {
        // Arrange
        String accountId = uniqueAccountId("ACC-004");
        Transaction transaction1 = createTestTransaction(accountId, "MERCH-001");
        Transaction transaction2 = createTestTransaction(accountId, "MERCH-001"); // Same merchant
        Transaction transaction3 = createTestTransaction(accountId, "MERCH-002");

        // Act
        velocityService.incrementCounters(transaction1);
        velocityService.incrementCounters(transaction2);
        velocityService.incrementCounters(transaction3);

        // Assert
        VelocityMetrics metrics = velocityService.findVelocityMetricsByTransaction(transaction3);
        assertThat(metrics.getTransactionCount(FIVE_MINUTES)).isEqualTo(3L);
        assertThat(metrics.getUniqueMerchants(FIVE_MINUTES)).isEqualTo(2L); // Only 2 unique merchants
    }

    @Test
    @DisplayName("Should track unique locations using HyperLogLog")
    void shouldTrackUniqueLocationsUsingHyperLogLog() {
        // Arrange
        String accountId = uniqueAccountId("ACC-005");
        Transaction transaction1 = createTransactionWithLocation(accountId, 40.7128, -74.0060);
        Transaction transaction2 = createTransactionWithLocation(accountId, 40.7128, -74.0060); // Same location
        Transaction transaction3 = createTransactionWithLocation(accountId, 51.5074, -0.1278);

        // Act
        velocityService.incrementCounters(transaction1);
        velocityService.incrementCounters(transaction2);
        velocityService.incrementCounters(transaction3);

        // Assert
        VelocityMetrics metrics = velocityService.findVelocityMetricsByTransaction(transaction3);
        assertThat(metrics.getTransactionCount(FIVE_MINUTES)).isEqualTo(3L);
        assertThat(metrics.getUniqueLocations(FIVE_MINUTES)).isEqualTo(2L); // Only 2 unique locations
    }

    @Test
    @DisplayName("Should cache velocity metrics after first fetch")
    void shouldCacheVelocityMetricsAfterFirstFetch() {
        String accountId = uniqueAccountId("ACC-006");
        Transaction transaction = createTestTransaction(accountId, "MERCH-001");
        velocityService.incrementCounters(transaction);

        VelocityMetrics metrics1 = velocityService.findVelocityMetricsByTransaction(transaction);
        assertThat(metrics1).isNotNull();
        assertThat(metrics1.getTransactionCount(FIVE_MINUTES)).isEqualTo(1L);

        cleanupRedis();

        VelocityMetrics metrics2 = velocityService.findVelocityMetricsByTransaction(transaction);
        assertThat(metrics2).isNotNull();
        assertThat(metrics2.getTransactionCount(FIVE_MINUTES)).isZero();
    }

    @Test
    @DisplayName("Should evict cache after incrementing counters")
    void shouldEvictCacheAfterIncrementingCounters() {
        // Arrange
        String accountId = uniqueAccountId("ACC-007");
        Transaction transaction = createTestTransaction(accountId, "MERCH-001");
        velocityService.incrementCounters(transaction);

        // Cache the metrics
        VelocityMetrics metrics1 = velocityService.findVelocityMetricsByTransaction(transaction);
        assertThat(metrics1.getTransactionCount(FIVE_MINUTES)).isEqualTo(1L);

        // Act - Increment again (should evict cache)
        velocityService.incrementCounters(transaction);

        // Assert - Should fetch fresh data from Redis
        VelocityMetrics metrics2 = velocityService.findVelocityMetricsByTransaction(transaction);
        assertThat(metrics2.getTransactionCount(FIVE_MINUTES)).isEqualTo(2L);
    }

    @Test
    @DisplayName("Should track amounts correctly with different values")
    void shouldTrackAmountsCorrectlyWithDifferentValues() {
        // Arrange
        String accountId = uniqueAccountId("ACC-008");
        Transaction transaction1 = createTransactionWithAmount(accountId, new BigDecimal("50.00"));
        Transaction transaction2 = createTransactionWithAmount(accountId, new BigDecimal("75.50"));
        Transaction transaction3 = createTransactionWithAmount(accountId, new BigDecimal("124.99"));

        // Act
        velocityService.incrementCounters(transaction1);
        velocityService.incrementCounters(transaction2);
        velocityService.incrementCounters(transaction3);

        // Assert
        VelocityMetrics metrics = velocityService.findVelocityMetricsByTransaction(transaction3);
        BigDecimal expectedTotal = new BigDecimal("250.49");
        assertThat(metrics.getTotalAmount(FIVE_MINUTES)).isEqualByComparingTo(expectedTotal);
    }

//    @Test
//    @DisplayName("Should test private method findTransactionCounts using reflection")
//    void shouldTestFindTransactionCountsUsingReflection() throws Exception {
//        // Arrange
//        String accountId = uniqueAccountId("ACC-009");
//        Transaction transaction = createTestTransaction(accountId, "MERCH-001");
//        velocityService.incrementCounters(transaction);
//
//        // Act - Use reflection to access private method
//        Method method = VelocityCounterAdapter.class.getDeclaredMethod("findTransactionCounts", Transaction.class);
//        method.setAccessible(true);
//
//        @SuppressWarnings("unchecked")
//        Map<TimeWindow, Long> counts = (Map<TimeWindow, Long>) method.invoke(velocityService, transaction);
//
//        // Assert
//        assertThat(counts)
//                .isNotNull()
//                .hasSize(3)
//                .containsEntry(FIVE_MINUTES, 1L)
//                .containsEntry(ONE_HOUR, 1L)
//                .containsEntry(TWENTY_FOUR_HOURS, 1L);
//    }

//    @Test
//    @DisplayName("Should test private method findTotalAmounts using reflection")
//    void shouldTestFindTotalAmountsUsingReflection() throws Exception {
//        // Arrange
//        String accountId = uniqueAccountId("ACC-010");
//        Transaction transaction = createTransactionWithAmount(accountId, new BigDecimal("150.75"));
//        velocityService.incrementCounters(transaction);
//
//        // Act - Use reflection to access private method
//        Method method = VelocityCounterAdapter.class.getDeclaredMethod("findTotalAmounts", Transaction.class);
//        method.setAccessible(true);
//
//        @SuppressWarnings("unchecked")
//        Map<TimeWindow, BigDecimal> amounts = (Map<TimeWindow, BigDecimal>) method.invoke(velocityService, transaction);
//
//        // Assert
//        assertThat(amounts)
//                .isNotNull()
//                .hasSize(3);
//        assertThat(amounts.get(FIVE_MINUTES)).isEqualByComparingTo(new BigDecimal("150.75"));
//    }

//    @Test
//    @DisplayName("Should test private method findMerchantCounts using reflection")
//    void shouldTestFindMerchantCountsUsingReflection() throws Exception {
//        // Arrange
//        String accountId = uniqueAccountId("ACC-011");
//        velocityService.incrementCounters(createTestTransaction(accountId, "MERCH-001"));
//        velocityService.incrementCounters(createTestTransaction(accountId, "MERCH-002"));
//        Transaction transaction = createTestTransaction(accountId, "MERCH-003");
//
//        // Act - Use reflection to access private method
//        Method method = VelocityCounterAdapter.class.getDeclaredMethod("findMerchantCounts", Transaction.class);
//        method.setAccessible(true);
//
//        @SuppressWarnings("unchecked")
//        Map<TimeWindow, Long> merchantCounts = (Map<TimeWindow, Long>) method.invoke(velocityService, transaction);
//
//        // Assert
//        assertThat(merchantCounts)
//                .isNotNull()
//                .hasSize(3)
//                .containsEntry(FIVE_MINUTES, 2L);
//    }

//    @Test
//    @DisplayName("Should test private method findLocationCounts using reflection")
//    void shouldTestFindLocationCountsUsingReflection() throws Exception {
//        // Arrange
//        String accountId = uniqueAccountId("ACC-012");
//        velocityService.incrementCounters(createTransactionWithLocation(accountId, 40.7128, -74.0060));
//        velocityService.incrementCounters(createTransactionWithLocation(accountId, 51.5074, -0.1278));
//        Transaction transaction = createTransactionWithLocation(accountId, 48.8566, 2.3522);
//
//        // Act - Use reflection to access private method
//        Method method = VelocityCounterAdapter.class.getDeclaredMethod("findLocationCounts", Transaction.class);
//        method.setAccessible(true);
//
//        @SuppressWarnings("unchecked")
//        Map<TimeWindow, Long> locationCounts = (Map<TimeWindow, Long>) method.invoke(velocityService, transaction);
//
//        // Assert
//        assertThat(locationCounts)
//                .isNotNull()
//                .hasSize(3)
//                .containsEntry(FIVE_MINUTES, 2L);
//    }

    @Test
    @DisplayName("Should separate metrics by account ID")
    void shouldSeparateMetricsByAccountId() {
        // Arrange
        Transaction transaction1 = createTestTransaction(uniqueAccountId("ACC-013-A"), "MERCH-001");
        Transaction transaction2 = createTestTransaction(uniqueAccountId("ACC-013-B"), "MERCH-001");

        // Act
        velocityService.incrementCounters(transaction1);
        velocityService.incrementCounters(transaction1);
        velocityService.incrementCounters(transaction2);

        // Assert
        VelocityMetrics metricsA = velocityService.findVelocityMetricsByTransaction(transaction1);
        VelocityMetrics metricsB = velocityService.findVelocityMetricsByTransaction(transaction2);

        assertThat(metricsA.getTransactionCount(FIVE_MINUTES)).isEqualTo(2L);
        assertThat(metricsB.getTransactionCount(FIVE_MINUTES)).isEqualTo(1L);
    }

    @Test
    @DisplayName("Should set correct expiration times for transaction counters")
    void shouldSetCorrectExpirationTimesForTransactionCounters() {
        // Arrange
        String accountId = uniqueAccountId("ACC-014");
        Transaction transaction = createTestTransaction(accountId, "MERCH-001");

        // Act
        velocityService.incrementCounters(transaction);

        // Assert - Check TTL for each time window
        long ttl5Min = getKeyTtl(buildTransactionCounterKey(transaction, FIVE_MINUTES));
        long ttl1Hour = getKeyTtl(buildTransactionCounterKey(transaction, ONE_HOUR));
        long ttl24Hours = getKeyTtl(buildTransactionCounterKey(transaction, TWENTY_FOUR_HOURS));

        // Allow some margin for execution time (within 10 seconds of expected)
        assertThat(ttl5Min).isBetween(FIVE_MINUTES.getDuration().toSeconds() - 10, FIVE_MINUTES.getDuration().toSeconds());
        assertThat(ttl1Hour).isBetween(ONE_HOUR.getDuration().toSeconds() - 10, ONE_HOUR.getDuration().toSeconds());
        assertThat(ttl24Hours).isBetween(TWENTY_FOUR_HOURS.getDuration().toSeconds() - 10, TWENTY_FOUR_HOURS.getDuration().toSeconds());
    }

    @Test
    @DisplayName("Should set correct expiration times for total amount counters")
    void shouldSetCorrectExpirationTimesForTotalAmountCounters() {
        // Arrange
        String accountId = uniqueAccountId("ACC-015");
        Transaction transaction = createTestTransaction(accountId, "MERCH-001");

        // Act
        velocityService.incrementCounters(transaction);

        // Assert - Check TTL for amount counters
        long ttl5Min = getKeyTtl(buildTotalAmountKey(transaction, FIVE_MINUTES));
        long ttl1Hour = getKeyTtl(buildTotalAmountKey(transaction, ONE_HOUR));
        long ttl24Hours = getKeyTtl(buildTotalAmountKey(transaction, TWENTY_FOUR_HOURS));

        assertThat(ttl5Min).isBetween(FIVE_MINUTES.getDuration().toSeconds() - 10, FIVE_MINUTES.getDuration().toSeconds());
        assertThat(ttl1Hour).isBetween(ONE_HOUR.getDuration().toSeconds() - 10, ONE_HOUR.getDuration().toSeconds());
        assertThat(ttl24Hours).isBetween(TWENTY_FOUR_HOURS.getDuration().toSeconds() - 10, TWENTY_FOUR_HOURS.getDuration().toSeconds());
    }

    @Test
    @DisplayName("Should set correct expiration times for merchant HyperLogLog")
    void shouldSetCorrectExpirationTimesForMerchantHyperLogLog() {
        // Arrange
        String accountId = uniqueAccountId("ACC-016");
        Transaction transaction = createTestTransaction(accountId, "MERCH-001");

        // Act
        velocityService.incrementCounters(transaction);

        // Assert - Check TTL for merchant HyperLogLog
        long ttl5Min = getKeyTtl(buildMerchantKey(transaction, FIVE_MINUTES));
        long ttl1Hour = getKeyTtl(buildMerchantKey(transaction, ONE_HOUR));
        long ttl24Hours = getKeyTtl(buildMerchantKey(transaction, TWENTY_FOUR_HOURS));

        assertThat(ttl5Min).isBetween(FIVE_MINUTES.getDuration().toSeconds() - 10, FIVE_MINUTES.getDuration().toSeconds());
        assertThat(ttl1Hour).isBetween(ONE_HOUR.getDuration().toSeconds() - 10, ONE_HOUR.getDuration().toSeconds());
        assertThat(ttl24Hours).isBetween(TWENTY_FOUR_HOURS.getDuration().toSeconds() - 10, TWENTY_FOUR_HOURS.getDuration().toSeconds());
    }

    @Test
    @DisplayName("Should set correct expiration times for location HyperLogLog")
    void shouldSetCorrectExpirationTimesForLocationHyperLogLog() {
        // Arrange
        String accountId = uniqueAccountId("ACC-017");
        Transaction transaction = createTestTransaction(accountId, "MERCH-001");

        // Act
        velocityService.incrementCounters(transaction);

        // Assert - Check TTL for location HyperLogLog
        long ttl5Min = getKeyTtl(buildLocationKey(transaction, FIVE_MINUTES));
        long ttl1Hour = getKeyTtl(buildLocationKey(transaction, ONE_HOUR));
        long ttl24Hours = getKeyTtl(buildLocationKey(transaction, TWENTY_FOUR_HOURS));

        assertThat(ttl5Min).isBetween(FIVE_MINUTES.getDuration().toSeconds() - 10, FIVE_MINUTES.getDuration().toSeconds());
        assertThat(ttl1Hour).isBetween(ONE_HOUR.getDuration().toSeconds() - 10, ONE_HOUR.getDuration().toSeconds());
        assertThat(ttl24Hours).isBetween(TWENTY_FOUR_HOURS.getDuration().toSeconds() - 10, TWENTY_FOUR_HOURS.getDuration().toSeconds());
    }

    @Test
    @DisplayName("Should refresh expiration time on subsequent increments")
    void shouldRefreshExpirationTimeOnSubsequentIncrements() {
        // Arrange
        String accountId = uniqueAccountId("ACC-018");
        Transaction transaction = createTestTransaction(accountId, "MERCH-001");

        // Act - First increment
        velocityService.incrementCounters(transaction);
        long initialTtl = getKeyTtl(buildTransactionCounterKey(transaction, FIVE_MINUTES));

        // Wait 2 seconds
        await().pollDelay(2, SECONDS).until(() -> true);

        // Second increment - should refresh TTL
        velocityService.incrementCounters(transaction);
        long refreshedTtl = getKeyTtl(buildTransactionCounterKey(transaction, FIVE_MINUTES));

        // Assert - Refreshed TTL should be greater than (initial TTL - 2 seconds)
        assertThat(refreshedTtl).isGreaterThan(initialTtl - 2)
                .isCloseTo(FIVE_MINUTES.getDuration().toSeconds(), within(10L));
    }

    // Helper methods for Redis key access
    private long getKeyTtl(String key) {
        return redisTemplate.getExpire(key);
    }

    // Key constants matching VelocityCounterAdapter
    private static final String TRANSACTION_COUNTER_KEY = "velocity:transaction:counter";
    private static final String MERCHANTS_KEY = "velocity:merchants";
    private static final String LOCATIONS_KEY = "velocity:locations";
    private static final String TOTAL_AMOUNT_KEY = "velocity:amount";

    private String buildTransactionCounterKey(Transaction transaction, TimeWindow timeWindow) {
        return TRANSACTION_COUNTER_KEY + ":%s:%s".formatted(timeWindow.getLabel(), transaction.accountId());
    }

    private String buildMerchantKey(Transaction transaction, TimeWindow timeWindow) {
        return MERCHANTS_KEY + ":%s:%s".formatted(timeWindow.getLabel(), transaction.accountId());
    }

    private String buildLocationKey(Transaction transaction, TimeWindow timeWindow) {
        return LOCATIONS_KEY + ":%s:%s".formatted(timeWindow.getLabel(), transaction.accountId());
    }

    private String buildTotalAmountKey(Transaction transaction, TimeWindow timeWindow) {
        return TOTAL_AMOUNT_KEY + ":%s:%s".formatted(timeWindow.getLabel(), transaction.accountId());
    }

    private Transaction createTestTransaction(String accountId, String merchantId) {
        return Transaction.builder()
                .id(TransactionId.generate())
                .accountId(accountId)
                .amount(new Money(new BigDecimal("100.00"), Currency.getInstance("USD")))
                .type(TransactionType.PURCHASE)
                .channel(Channel.ONLINE)
                .merchant(new Merchant(MerchantId.of(merchantId), "Test Merchant", MerchantCategory.RETAIL))
                .location(Location.of(40.7128, -74.0060))
                .deviceId("DEVICE-001")
                .timestamp(Instant.now())
                .build();
    }

    private Transaction createTransactionWithAmount(String accountId, BigDecimal amount) {
        return Transaction.builder()
                .id(TransactionId.generate())
                .accountId(accountId)
                .amount(new Money(amount, Currency.getInstance("USD")))
                .type(TransactionType.PURCHASE)
                .channel(Channel.ONLINE)
                .merchant(new Merchant(MerchantId.of("MERCH-001"), "Test Merchant", MerchantCategory.RETAIL))
                .location(Location.of(40.7128, -74.0060))
                .deviceId("DEVICE-001")
                .timestamp(Instant.now())
                .build();
    }

    private Transaction createTransactionWithLocation(String accountId, double latitude, double longitude) {
        return Transaction.builder()
                .id(TransactionId.generate())
                .accountId(accountId)
                .amount(new Money(new BigDecimal("100.00"), Currency.getInstance("USD")))
                .type(TransactionType.PURCHASE)
                .channel(Channel.ONLINE)
                .merchant(new Merchant(MerchantId.of("MERCH-001"), "Test Merchant", MerchantCategory.RETAIL))
                .location(Location.of(latitude, longitude))
                .deviceId("DEVICE-001")
                .timestamp(Instant.now())
                .build();
    }
}
