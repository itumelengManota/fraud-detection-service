package com.twenty9ine.frauddetection.infrastructure.adapter.cache;

import com.twenty9ine.frauddetection.domain.valueobject.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.redisson.Redisson;
import org.redisson.api.RAtomicDouble;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RHyperLogLog;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static com.twenty9ine.frauddetection.infrastructure.adapter.cache.VelocityCounterAdapter.*;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

import static org.assertj.core.api.Assertions.*;

import static com.twenty9ine.frauddetection.domain.valueobject.TimeWindow.*;

@Testcontainers
//@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Execution(ExecutionMode.CONCURRENT)
@ResourceLock("redis")
class VelocityCounterAdapterIntegrationTest {

    @Container
    private static final GenericContainer<?> redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withReuse(true);

    private static RedissonClient redissonClient;
    private static CacheManager cacheManager;
    private VelocityCounterAdapter adapter;

    @BeforeAll
    static void beforeAll() {
        Config config = new Config();
        config.useSingleServer().setAddress(String.format("redis://%s:%d", redisContainer.getHost(), redisContainer.getFirstMappedPort()));

        redissonClient = Redisson.create(config);
        cacheManager = new CaffeineCacheManager("velocityMetrics");
    }

    @AfterAll
    static void afterAll() {
        if (redissonClient != null) {
            redissonClient.shutdown();
        }
    }

    @BeforeEach
    void setUp() {
        adapter = new VelocityCounterAdapter(redissonClient, cacheManager);
        redissonClient.getKeys().flushall();
        cacheManager.getCacheNames().forEach(name -> Objects.requireNonNull(cacheManager.getCache(name)).clear());
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
        VelocityMetrics metrics = adapter.findVelocityMetricsByTransaction(transaction);

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
        adapter.incrementCounters(transaction);

        // Assert
        VelocityMetrics metrics = adapter.findVelocityMetricsByTransaction(transaction);
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
        adapter.incrementCounters(transaction1);
        adapter.incrementCounters(transaction2);
        adapter.incrementCounters(transaction3);

        // Assert
        VelocityMetrics metrics = adapter.findVelocityMetricsByTransaction(transaction3);
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
        adapter.incrementCounters(transaction1);
        adapter.incrementCounters(transaction2);
        adapter.incrementCounters(transaction3);

        // Assert
        VelocityMetrics metrics = adapter.findVelocityMetricsByTransaction(transaction3);
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
        adapter.incrementCounters(transaction1);
        adapter.incrementCounters(transaction2);
        adapter.incrementCounters(transaction3);

        // Assert
        VelocityMetrics metrics = adapter.findVelocityMetricsByTransaction(transaction3);
        assertThat(metrics.getTransactionCount(FIVE_MINUTES)).isEqualTo(3L);
        assertThat(metrics.getUniqueLocations(FIVE_MINUTES)).isEqualTo(2L); // Only 2 unique locations
    }

    @Test
    @DisplayName("Should cache velocity metrics after first fetch")
    void shouldCacheVelocityMetricsAfterFirstFetch() {
        // Arrange
        String accountId = uniqueAccountId("ACC-006");
        Transaction transaction = createTestTransaction(accountId, "MERCH-001");
        adapter.incrementCounters(transaction);

        // Act - First fetch fromDate Redis
        VelocityMetrics metrics1 = adapter.findVelocityMetricsByTransaction(transaction);
        assertThat(metrics1).isNotNull();

        // Clear Redis but keep cache
        redissonClient.getKeys().flushall();

        // Act - Second fetch fromDate cache
        VelocityMetrics metrics2 = adapter.findVelocityMetricsByTransaction(transaction);

        // Assert - Should still return cached data
        assertThat(metrics2).isNotNull();
        assertThat(metrics2.getTransactionCount(FIVE_MINUTES)).isEqualTo(1L);
        assertThat(metrics2.getTotalAmount(FIVE_MINUTES)).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Should evict cache after incrementing counters")
    void shouldEvictCacheAfterIncrementingCounters() {
        // Arrange
        String accountId = uniqueAccountId("ACC-007");
        Transaction transaction = createTestTransaction(accountId, "MERCH-001");
        adapter.incrementCounters(transaction);

        // Cache the metrics
        VelocityMetrics metrics1 = adapter.findVelocityMetricsByTransaction(transaction);
        assertThat(metrics1.getTransactionCount(FIVE_MINUTES)).isEqualTo(1L);

        // Act - Increment again (should evict cache)
        adapter.incrementCounters(transaction);

        // Assert - Should fetch fresh data fromDate Redis
        VelocityMetrics metrics2 = adapter.findVelocityMetricsByTransaction(transaction);
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
        adapter.incrementCounters(transaction1);
        adapter.incrementCounters(transaction2);
        adapter.incrementCounters(transaction3);

        // Assert
        VelocityMetrics metrics = adapter.findVelocityMetricsByTransaction(transaction3);
        BigDecimal expectedTotal = new BigDecimal("250.49");
        assertThat(metrics.getTotalAmount(FIVE_MINUTES)).isEqualByComparingTo(expectedTotal);
    }

    @Test
    @DisplayName("Should test private method findTransactionCounts using reflection")
    void shouldTestFindTransactionCountsUsingReflection() throws Exception {
        // Arrange
        String accountId = uniqueAccountId("ACC-009");
        Transaction transaction = createTestTransaction(accountId, "MERCH-001");
        adapter.incrementCounters(transaction);

        // Act - Use reflection to access private method
        Method method = VelocityCounterAdapter.class.getDeclaredMethod("findTransactionCounts", Transaction.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<TimeWindow, Long> counts = (Map<TimeWindow, Long>) method.invoke(adapter, transaction);

        // Assert
        assertThat(counts)
                .isNotNull()
                .hasSize(3)
                .containsEntry(FIVE_MINUTES, 1L)
                .containsEntry(ONE_HOUR, 1L)
                .containsEntry(TWENTY_FOUR_HOURS, 1L);
    }

    @Test
    @DisplayName("Should test private method findTotalAmounts using reflection")
    void shouldTestFindTotalAmountsUsingReflection() throws Exception {
        // Arrange
        String accountId = uniqueAccountId("ACC-010");
        Transaction transaction = createTransactionWithAmount(accountId, new BigDecimal("150.75"));
        adapter.incrementCounters(transaction);

        // Act - Use reflection to access private method
        Method method = VelocityCounterAdapter.class.getDeclaredMethod("findTotalAmounts", Transaction.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<TimeWindow, BigDecimal> amounts = (Map<TimeWindow, BigDecimal>) method.invoke(adapter, transaction);

        // Assert
        assertThat(amounts)
                .isNotNull()
                .hasSize(3);
        assertThat(amounts.get(FIVE_MINUTES)).isEqualByComparingTo(new BigDecimal("150.75"));
    }

    @Test
    @DisplayName("Should test private method findMerchantCounts using reflection")
    void shouldTestFindMerchantCountsUsingReflection() throws Exception {
        // Arrange
        String accountId = uniqueAccountId("ACC-011");
        adapter.incrementCounters(createTestTransaction(accountId, "MERCH-001"));
        adapter.incrementCounters(createTestTransaction(accountId, "MERCH-002"));
        Transaction transaction = createTestTransaction(accountId, "MERCH-003");

        // Act - Use reflection to access private method
        Method method = VelocityCounterAdapter.class.getDeclaredMethod("findMerchantCounts", Transaction.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<TimeWindow, Long> merchantCounts = (Map<TimeWindow, Long>) method.invoke(adapter, transaction);

        // Assert
        assertThat(merchantCounts)
                .isNotNull()
                .hasSize(3)
                .containsEntry(FIVE_MINUTES, 2L);
    }

    @Test
    @DisplayName("Should test private method findLocationCounts using reflection")
    void shouldTestFindLocationCountsUsingReflection() throws Exception {
        // Arrange
        String accountId = uniqueAccountId("ACC-012");
        adapter.incrementCounters(createTransactionWithLocation(accountId, 40.7128, -74.0060));
        adapter.incrementCounters(createTransactionWithLocation(accountId, 51.5074, -0.1278));
        Transaction transaction = createTransactionWithLocation(accountId, 48.8566, 2.3522);

        // Act - Use reflection to access private method
        Method method = VelocityCounterAdapter.class.getDeclaredMethod("findLocationCounts", Transaction.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<TimeWindow, Long> locationCounts = (Map<TimeWindow, Long>) method.invoke(adapter, transaction);

        // Assert
        assertThat(locationCounts)
                .isNotNull()
                .hasSize(3)
                .containsEntry(FIVE_MINUTES, 2L);
    }

    @Test
    @DisplayName("Should separate metrics by account ID")
    void shouldSeparateMetricsByAccountId() {
        // Arrange
        Transaction transaction1 = createTestTransaction(uniqueAccountId("ACC-013-A"), "MERCH-001");
        Transaction transaction2 = createTestTransaction(uniqueAccountId("ACC-013-B"), "MERCH-001");

        // Act
        adapter.incrementCounters(transaction1);
        adapter.incrementCounters(transaction1);
        adapter.incrementCounters(transaction2);

        // Assert
        VelocityMetrics metricsA = adapter.findVelocityMetricsByTransaction(transaction1);
        VelocityMetrics metricsB = adapter.findVelocityMetricsByTransaction(transaction2);

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
        adapter.incrementCounters(transaction);

        // Assert - Check TTL for each time window
        long ttl5Min = findTransactionCounter(transaction, FIVE_MINUTES).remainTimeToLive();
        long ttl1Hour = findTransactionCounter(transaction, ONE_HOUR).remainTimeToLive();
        long ttl24Hours = findTransactionCounter(transaction, TWENTY_FOUR_HOURS).remainTimeToLive();

        // Allow some margin for execution time (within 10 seconds of expected)
        assertThat(ttl5Min).isBetween(FIVE_MINUTES.getDuration().toMillis() - 10000, FIVE_MINUTES.getDuration().toMillis());
        assertThat(ttl1Hour).isBetween(ONE_HOUR.getDuration().toMillis() - 10000, ONE_HOUR.getDuration().toMillis());
        assertThat(ttl24Hours).isBetween(TWENTY_FOUR_HOURS.getDuration().toMillis() - 10000, TWENTY_FOUR_HOURS.getDuration().toMillis());
    }

    @Test
    @DisplayName("Should set correct expiration times for total amount counters")
    void shouldSetCorrectExpirationTimesForTotalAmountCounters() {
        // Arrange
        String accountId = uniqueAccountId("ACC-015");
        Transaction transaction = createTestTransaction(accountId, "MERCH-001");

        // Act
        adapter.incrementCounters(transaction);

        // Assert - Check TTL for amount counters
        long ttl5Min = findTotalAmountCounter(transaction, FIVE_MINUTES).remainTimeToLive();
        long ttl1Hour = findTotalAmountCounter(transaction, ONE_HOUR).remainTimeToLive();
        long ttl24Hours = findTotalAmountCounter(transaction, TWENTY_FOUR_HOURS).remainTimeToLive();

        assertThat(ttl5Min).isBetween(FIVE_MINUTES.getDuration().toMillis() - 10000, FIVE_MINUTES.getDuration().toMillis());
        assertThat(ttl1Hour).isBetween(ONE_HOUR.getDuration().toMillis() - 10000, ONE_HOUR.getDuration().toMillis());
        assertThat(ttl24Hours).isBetween(TWENTY_FOUR_HOURS.getDuration().toMillis() - 10000, TWENTY_FOUR_HOURS.getDuration().toMillis());
    }

    @Test
    @DisplayName("Should set correct expiration times for merchant HyperLogLog")
    void shouldSetCorrectExpirationTimesForMerchantHyperLogLog() {
        // Arrange
        String accountId = uniqueAccountId("ACC-016");
        Transaction transaction = createTestTransaction(accountId, "MERCH-001");

        // Act
        adapter.incrementCounters(transaction);

        // Assert - Check TTL for merchant HyperLogLog
        long ttl5Min = findMerchantCounter(transaction, FIVE_MINUTES).remainTimeToLive();
        long ttl1Hour = findMerchantCounter(transaction, ONE_HOUR).remainTimeToLive();
        long ttl24Hours = findMerchantCounter(transaction, TWENTY_FOUR_HOURS).remainTimeToLive();

        assertThat(ttl5Min).isBetween(FIVE_MINUTES.getDuration().toMillis() - 10000, FIVE_MINUTES.getDuration().toMillis());
        assertThat(ttl1Hour).isBetween(ONE_HOUR.getDuration().toMillis() - 10000, ONE_HOUR.getDuration().toMillis());
        assertThat(ttl24Hours).isBetween(TWENTY_FOUR_HOURS.getDuration().toMillis() - 10000, TWENTY_FOUR_HOURS.getDuration().toMillis());
    }

    @Test
    @DisplayName("Should set correct expiration times for location HyperLogLog")
    void shouldSetCorrectExpirationTimesForLocationHyperLogLog() {
        // Arrange
        String accountId = uniqueAccountId("ACC-017");
        Transaction transaction = createTestTransaction(accountId, "MERCH-001");

        // Act
        adapter.incrementCounters(transaction);

        // Assert - Check TTL for location HyperLogLog
        long ttl5Min = findLocationCounter(transaction, FIVE_MINUTES).remainTimeToLive();
        long ttl1Hour = findLocationCounter(transaction, ONE_HOUR).remainTimeToLive();
        long ttl24Hours = findLocationCounter(transaction, TWENTY_FOUR_HOURS).remainTimeToLive();

        assertThat(ttl5Min).isBetween(FIVE_MINUTES.getDuration().toMillis() - 10000, FIVE_MINUTES.getDuration().toMillis());
        assertThat(ttl1Hour).isBetween(ONE_HOUR.getDuration().toMillis() - 10000, ONE_HOUR.getDuration().toMillis());
        assertThat(ttl24Hours).isBetween(TWENTY_FOUR_HOURS.getDuration().toMillis() - 10000, TWENTY_FOUR_HOURS.getDuration().toMillis());
    }

    @Test
    @DisplayName("Should refresh expiration time on subsequent increments")
    void shouldRefreshExpirationTimeOnSubsequentIncrements() {
        // Arrange
        String accountId = uniqueAccountId("ACC-018");
        Transaction transaction = createTestTransaction(accountId, "MERCH-001");

        // Act - First increment
        adapter.incrementCounters(transaction);
        long initialTtl = findTransactionCounter(transaction, FIVE_MINUTES).remainTimeToLive();

        // Wait 2 seconds
        await().pollDelay(2, SECONDS).until(() -> true);

        // Second increment - should refresh TTL
        adapter.incrementCounters(transaction);
        long refreshedTtl = findTransactionCounter(transaction, FIVE_MINUTES).remainTimeToLive();

        // Assert - Refreshed TTL should be greater than (initial TTL - 2 seconds)
        assertThat(refreshedTtl).isGreaterThan(initialTtl - 2000)
                                .isCloseTo(FIVE_MINUTES.getDuration().toMillis(), within(10000L));
    }

    private RAtomicLong findTransactionCounter(Transaction transaction, TimeWindow timeWindow) {
        return redissonClient.getAtomicLong(TRANSACTION_COUNTER_KEY + ":%s:%s".formatted(timeWindow.getLabel(), transaction.accountId()));
    }

    private RHyperLogLog<String> findMerchantCounter(Transaction transaction, TimeWindow timeWindow) {
        return redissonClient.getHyperLogLog(MERCHANTS_COUNTER_KEY + ":%s:%s".formatted(timeWindow.getLabel(), transaction.accountId()));
    }

    private RHyperLogLog<String> findLocationCounter(Transaction transaction, TimeWindow timeWindow) {
        return redissonClient.getHyperLogLog(LOCATIONS_COUNTER_KEY + ":%s:%s".formatted(timeWindow.getLabel(), transaction.accountId()));
    }

    private RAtomicDouble findTotalAmountCounter(Transaction transaction, TimeWindow timeWindow) {
        return redissonClient.getAtomicDouble(TOTAL_AMOUNT_COUNTER_KEY + ":%s:%s".formatted(timeWindow.getLabel(), transaction.accountId()));
    }

    private Transaction createTestTransaction(String accountId, String merchantId) {
        return Transaction.builder()
                .id(TransactionId.generate())
                .accountId(accountId)
                .amount(new Money(new BigDecimal("100.00"), Currency.getInstance("USD")))
                .type(TransactionType.PURCHASE)
                .channel(Channel.ONLINE)
                .merchant(new Merchant(MerchantId.of(merchantId), "Test Merchant", "Retail"))
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
                .merchant(new Merchant(MerchantId.of("MERCH-001"), "Test Merchant", "Retail"))
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
                .merchant(new Merchant(MerchantId.of("MERCH-001"), "Test Merchant", "Retail"))
                .location(Location.of(latitude, longitude))
                .deviceId("DEVICE-001")
                .timestamp(Instant.now())
                .build();
    }
}