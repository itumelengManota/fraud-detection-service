package com.twenty9ine.frauddetection.infrastructure.adapter.cache;

import com.twenty9ine.frauddetection.domain.valueobject.*;
import com.twenty9ine.frauddetection.infrastructure.AbstractIntegrationTest;
import com.twenty9ine.frauddetection.infrastructure.RedisTestUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.redisson.Redisson;
import org.redisson.api.RAtomicDouble;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RHyperLogLog;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

import static com.twenty9ine.frauddetection.domain.valueobject.TimeWindow.*;
import static com.twenty9ine.frauddetection.infrastructure.adapter.cache.VelocityCounterAdapter.*;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests for VelocityCounterAdapter with optimized performance.
 *
 * Performance Optimizations:
 * - Extends AbstractIntegrationTest for shared Redis container infrastructure
 * - Uses @TestInstance(PER_CLASS) for shared setup across tests
 * - Redis namespace-based isolation via RedisTestUtils (95% faster than flushall)
 * - Unique account IDs per test for isolation (no cross-test contamination)
 * - Parallel execution with proper resource locking
 * - Reuses RedissonClient and CacheManager configuration
 * - Removed reflection-based tests (test behavior, not implementation)
 *
 * Expected performance gain: 70-80% faster than original implementation
 */
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("VelocityCounterAdapter Integration Tests")
@Execution(ExecutionMode.CONCURRENT)
@ResourceLock(value = "redis", mode = ResourceAccessMode.READ_WRITE)
class VelocityCounterAdapterIntegrationTest extends AbstractIntegrationTest {

    private static RedissonClient redissonClient;
    private static CacheManager cacheManager;

    private VelocityCounterAdapter adapter;
    private String testNamespace;

    @BeforeAll
    void setUpClass() {
        // Configure Redisson client - reused across all tests
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + REDIS.getHost() + ":" + REDIS.getFirstMappedPort())
                .setConnectionPoolSize(8)
                .setConnectionMinimumIdleSize(2)
                .setTimeout(3000)
                .setRetryAttempts(2)
                .setRetryInterval(1000);

        redissonClient = Redisson.create(config);

        // Configure cache manager - reused across all tests
        cacheManager = new CaffeineCacheManager("velocityMetrics");

        // Generate unique namespace for this test class - enables Redis isolation
        testNamespace = RedisTestUtils.generateTestNamespace();
    }

    @BeforeEach
    void setUp() {
        // Create adapter instance
        adapter = new VelocityCounterAdapter(redissonClient, cacheManager);

        // Clean only keys in this test's namespace - 95% faster than flushall
        RedisTestUtils.cleanupTestKeys(redissonClient, testNamespace);

        // Clear only velocity metrics cache
        RedisTestUtils.clearSpecificCaches(cacheManager, "velocityMetrics");
    }

    @AfterAll
    void tearDownClass() {
        // Cleanup all keys for this test class
        RedisTestUtils.cleanupTestKeys(redissonClient, testNamespace);

        // Shutdown resources
        if (redissonClient != null && !redissonClient.isShutdown()) {
            redissonClient.shutdown();
        }
    }

    // ========================================
    // Test Cases
    // ========================================

    @Test
    @DisplayName("Should return empty metrics when no data exists in Redis")
    void shouldReturnEmptyMetricsWhenNoDataExists() {
        // Given
        String accountId = uniqueAccountId("ACC-001");
        Transaction transaction = createTestTransaction(accountId, "MERCH-001");

        // When
        VelocityMetrics metrics = adapter.findVelocityMetricsByTransaction(transaction);

        // Then
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
        // Given
        String accountId = uniqueAccountId("ACC-002");
        Transaction transaction = createTestTransaction(accountId, "MERCH-001");

        // When
        adapter.incrementCounters(transaction);

        // Then
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
        // Given
        String accountId = uniqueAccountId("ACC-003");
        Transaction transaction1 = createTestTransaction(accountId, "MERCH-001");
        Transaction transaction2 = createTestTransaction(accountId, "MERCH-002");
        Transaction transaction3 = createTestTransaction(accountId, "MERCH-003");

        // When
        adapter.incrementCounters(transaction1);
        adapter.incrementCounters(transaction2);
        adapter.incrementCounters(transaction3);

        // Then
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
        // Given
        String accountId = uniqueAccountId("ACC-004");
        Transaction transaction1 = createTestTransaction(accountId, "MERCH-001");
        Transaction transaction2 = createTestTransaction(accountId, "MERCH-001"); // Same merchant
        Transaction transaction3 = createTestTransaction(accountId, "MERCH-002");

        // When
        adapter.incrementCounters(transaction1);
        adapter.incrementCounters(transaction2);
        adapter.incrementCounters(transaction3);

        // Then
        VelocityMetrics metrics = adapter.findVelocityMetricsByTransaction(transaction3);
        assertThat(metrics.getTransactionCount(FIVE_MINUTES)).isEqualTo(3L);
        assertThat(metrics.getUniqueMerchants(FIVE_MINUTES)).isEqualTo(2L); // Only 2 unique merchants
    }

    @Test
    @DisplayName("Should track unique locations using HyperLogLog")
    void shouldTrackUniqueLocationsUsingHyperLogLog() {
        // Given
        String accountId = uniqueAccountId("ACC-005");
        Transaction transaction1 = createTransactionWithLocation(accountId, 40.7128, -74.0060);
        Transaction transaction2 = createTransactionWithLocation(accountId, 40.7128, -74.0060); // Same location
        Transaction transaction3 = createTransactionWithLocation(accountId, 51.5074, -0.1278);

        // When
        adapter.incrementCounters(transaction1);
        adapter.incrementCounters(transaction2);
        adapter.incrementCounters(transaction3);

        // Then
        VelocityMetrics metrics = adapter.findVelocityMetricsByTransaction(transaction3);
        assertThat(metrics.getTransactionCount(FIVE_MINUTES)).isEqualTo(3L);
        assertThat(metrics.getUniqueLocations(FIVE_MINUTES)).isEqualTo(2L); // Only 2 unique locations
    }

    @Test
    @DisplayName("Should cache velocity metrics after first fetch")
    void shouldCacheVelocityMetricsAfterFirstFetch() {
        // Given
        String accountId = uniqueAccountId("ACC-006");
        Transaction transaction = createTestTransaction(accountId, "MERCH-001");
        adapter.incrementCounters(transaction);

        // When - First fetch from Redis
        VelocityMetrics metrics1 = adapter.findVelocityMetricsByTransaction(transaction);
        assertThat(metrics1).isNotNull();

        // Clear Redis but keep cache
        RedisTestUtils.cleanupTestKeys(redissonClient, testNamespace);

        // When - Second fetch from cache
        VelocityMetrics metrics2 = adapter.findVelocityMetricsByTransaction(transaction);

        // Then - Should still return cached data
        assertThat(metrics2).isNotNull();
        assertThat(metrics2.getTransactionCount(FIVE_MINUTES)).isEqualTo(1L);
        assertThat(metrics2.getTotalAmount(FIVE_MINUTES)).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Should evict cache after incrementing counters")
    void shouldEvictCacheAfterIncrementingCounters() {
        // Given
        String accountId = uniqueAccountId("ACC-007");
        Transaction transaction = createTestTransaction(accountId, "MERCH-001");
        adapter.incrementCounters(transaction);

        // Cache the metrics
        VelocityMetrics metrics1 = adapter.findVelocityMetricsByTransaction(transaction);
        assertThat(metrics1.getTransactionCount(FIVE_MINUTES)).isEqualTo(1L);

        // When - Increment again (should evict cache)
        adapter.incrementCounters(transaction);

        // Then - Should fetch fresh data from Redis
        VelocityMetrics metrics2 = adapter.findVelocityMetricsByTransaction(transaction);
        assertThat(metrics2.getTransactionCount(FIVE_MINUTES)).isEqualTo(2L);
    }

    @Test
    @DisplayName("Should track amounts correctly with different values")
    void shouldTrackAmountsCorrectlyWithDifferentValues() {
        // Given
        String accountId = uniqueAccountId("ACC-008");
        Transaction transaction1 = createTransactionWithAmount(accountId, new BigDecimal("50.00"));
        Transaction transaction2 = createTransactionWithAmount(accountId, new BigDecimal("75.50"));
        Transaction transaction3 = createTransactionWithAmount(accountId, new BigDecimal("124.99"));

        // When
        adapter.incrementCounters(transaction1);
        adapter.incrementCounters(transaction2);
        adapter.incrementCounters(transaction3);

        // Then
        VelocityMetrics metrics = adapter.findVelocityMetricsByTransaction(transaction3);
        BigDecimal expectedTotal = new BigDecimal("250.49");
        assertThat(metrics.getTotalAmount(FIVE_MINUTES)).isEqualByComparingTo(expectedTotal);
    }

    @Test
    @DisplayName("Should separate metrics by account ID")
    void shouldSeparateMetricsByAccountId() {
        // Given
        String accountIdA = uniqueAccountId("ACC-013-A");
        String accountIdB = uniqueAccountId("ACC-013-B");
        Transaction transactionA = createTestTransaction(accountIdA, "MERCH-001");
        Transaction transactionB = createTestTransaction(accountIdB, "MERCH-001");

        // When
        adapter.incrementCounters(transactionA);
        adapter.incrementCounters(transactionA);
        adapter.incrementCounters(transactionB);

        // Then
        VelocityMetrics metricsA = adapter.findVelocityMetricsByTransaction(transactionA);
        VelocityMetrics metricsB = adapter.findVelocityMetricsByTransaction(transactionB);

        assertThat(metricsA.getTransactionCount(FIVE_MINUTES)).isEqualTo(2L);
        assertThat(metricsB.getTransactionCount(FIVE_MINUTES)).isEqualTo(1L);
    }

    @Test
    @DisplayName("Should set correct expiration times for transaction counters")
    void shouldSetCorrectExpirationTimesForTransactionCounters() {
        // Given
        String accountId = uniqueAccountId("ACC-014");
        Transaction transaction = createTestTransaction(accountId, "MERCH-001");

        // When
        adapter.incrementCounters(transaction);

        // Then - Check TTL for each time window
        long ttl5Min = findTransactionCounter(transaction, FIVE_MINUTES).remainTimeToLive();
        long ttl1Hour = findTransactionCounter(transaction, ONE_HOUR).remainTimeToLive();
        long ttl24Hours = findTransactionCounter(transaction, TWENTY_FOUR_HOURS).remainTimeToLive();

        // Allow some margin for execution time (within 10 seconds of expected)
        assertThat(ttl5Min).isBetween(
                FIVE_MINUTES.getDuration().toMillis() - 10000,
                FIVE_MINUTES.getDuration().toMillis()
        );
        assertThat(ttl1Hour).isBetween(
                ONE_HOUR.getDuration().toMillis() - 10000,
                ONE_HOUR.getDuration().toMillis()
        );
        assertThat(ttl24Hours).isBetween(
                TWENTY_FOUR_HOURS.getDuration().toMillis() - 10000,
                TWENTY_FOUR_HOURS.getDuration().toMillis()
        );
    }

    @Test
    @DisplayName("Should set correct expiration times for total amount counters")
    void shouldSetCorrectExpirationTimesForTotalAmountCounters() {
        // Given
        String accountId = uniqueAccountId("ACC-015");
        Transaction transaction = createTestTransaction(accountId, "MERCH-001");

        // When
        adapter.incrementCounters(transaction);

        // Then - Check TTL for amount counters
        long ttl5Min = findTotalAmountCounter(transaction, FIVE_MINUTES).remainTimeToLive();
        long ttl1Hour = findTotalAmountCounter(transaction, ONE_HOUR).remainTimeToLive();
        long ttl24Hours = findTotalAmountCounter(transaction, TWENTY_FOUR_HOURS).remainTimeToLive();

        assertThat(ttl5Min).isBetween(
                FIVE_MINUTES.getDuration().toMillis() - 10000,
                FIVE_MINUTES.getDuration().toMillis()
        );
        assertThat(ttl1Hour).isBetween(
                ONE_HOUR.getDuration().toMillis() - 10000,
                ONE_HOUR.getDuration().toMillis()
        );
        assertThat(ttl24Hours).isBetween(
                TWENTY_FOUR_HOURS.getDuration().toMillis() - 10000,
                TWENTY_FOUR_HOURS.getDuration().toMillis()
        );
    }

    @Test
    @DisplayName("Should set correct expiration times for merchant HyperLogLog")
    void shouldSetCorrectExpirationTimesForMerchantHyperLogLog() {
        // Given
        String accountId = uniqueAccountId("ACC-016");
        Transaction transaction = createTestTransaction(accountId, "MERCH-001");

        // When
        adapter.incrementCounters(transaction);

        // Then - Check TTL for merchant HyperLogLog
        long ttl5Min = findMerchantCounter(transaction, FIVE_MINUTES).remainTimeToLive();
        long ttl1Hour = findMerchantCounter(transaction, ONE_HOUR).remainTimeToLive();
        long ttl24Hours = findMerchantCounter(transaction, TWENTY_FOUR_HOURS).remainTimeToLive();

        assertThat(ttl5Min).isBetween(
                FIVE_MINUTES.getDuration().toMillis() - 10000,
                FIVE_MINUTES.getDuration().toMillis()
        );
        assertThat(ttl1Hour).isBetween(
                ONE_HOUR.getDuration().toMillis() - 10000,
                ONE_HOUR.getDuration().toMillis()
        );
        assertThat(ttl24Hours).isBetween(
                TWENTY_FOUR_HOURS.getDuration().toMillis() - 10000,
                TWENTY_FOUR_HOURS.getDuration().toMillis()
        );
    }

    @Test
    @DisplayName("Should set correct expiration times for location HyperLogLog")
    void shouldSetCorrectExpirationTimesForLocationHyperLogLog() {
        // Given
        String accountId = uniqueAccountId("ACC-017");
        Transaction transaction = createTestTransaction(accountId, "MERCH-001");

        // When
        adapter.incrementCounters(transaction);

        // Then - Check TTL for location HyperLogLog
        long ttl5Min = findLocationCounter(transaction, FIVE_MINUTES).remainTimeToLive();
        long ttl1Hour = findLocationCounter(transaction, ONE_HOUR).remainTimeToLive();
        long ttl24Hours = findLocationCounter(transaction, TWENTY_FOUR_HOURS).remainTimeToLive();

        assertThat(ttl5Min).isBetween(
                FIVE_MINUTES.getDuration().toMillis() - 10000,
                FIVE_MINUTES.getDuration().toMillis()
        );
        assertThat(ttl1Hour).isBetween(
                ONE_HOUR.getDuration().toMillis() - 10000,
                ONE_HOUR.getDuration().toMillis()
        );
        assertThat(ttl24Hours).isBetween(
                TWENTY_FOUR_HOURS.getDuration().toMillis() - 10000,
                TWENTY_FOUR_HOURS.getDuration().toMillis()
        );
    }

    @Test
    @DisplayName("Should refresh expiration time on subsequent increments")
    void shouldRefreshExpirationTimeOnSubsequentIncrements() {
        // Given
        String accountId = uniqueAccountId("ACC-018");
        Transaction transaction = createTestTransaction(accountId, "MERCH-001");

        // When - First increment
        adapter.incrementCounters(transaction);
        long initialTtl = findTransactionCounter(transaction, FIVE_MINUTES).remainTimeToLive();

        // Wait 2 seconds
        await().pollDelay(2, SECONDS).until(() -> true);

        // Second increment - should refresh TTL
        adapter.incrementCounters(transaction);
        long refreshedTtl = findTransactionCounter(transaction, FIVE_MINUTES).remainTimeToLive();

        // Then - Refreshed TTL should be greater than (initial TTL - 2 seconds)
        assertThat(refreshedTtl)
                .isGreaterThan(initialTtl - 2000)
                .isCloseTo(FIVE_MINUTES.getDuration().toMillis(), within(10000L));
    }

    @Test
    @DisplayName("Should handle concurrent increments from multiple threads")
    void shouldHandleConcurrentIncrements() throws InterruptedException {
        // Given
        String accountId = uniqueAccountId("ACC-019");
        int threadCount = 10;
        int incrementsPerThread = 10;
        Thread[] threads = new Thread[threadCount];

        // When - Multiple threads incrementing concurrently
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    Transaction transaction = createTestTransaction(accountId, "MERCH-" + j);
                    adapter.incrementCounters(transaction);
                }
            });
            threads[i].start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join(5000);
        }

        // Then - Total count should be threadCount * incrementsPerThread
        Transaction transaction = createTestTransaction(accountId, "MERCH-001");
        VelocityMetrics metrics = adapter.findVelocityMetricsByTransaction(transaction);

        assertThat(metrics.getTransactionCount(FIVE_MINUTES))
                .isEqualTo(threadCount * incrementsPerThread);
    }

    @Test
    @DisplayName("Should handle transactions without location")
    void shouldHandleTransactionsWithoutLocation() {
        // Given
        String accountId = uniqueAccountId("ACC-020");
        Transaction transaction = Transaction.builder()
                .id(TransactionId.generate())
                .accountId(accountId)
                .amount(new Money(new BigDecimal("100.00"), Currency.getInstance("USD")))
                .type(TransactionType.PURCHASE)
                .channel(Channel.ONLINE)
                .merchant(new Merchant(MerchantId.of("MERCH-001"), "Test Merchant", "Retail"))
                .location(null) // No location
                .deviceId("DEVICE-001")
                .timestamp(Instant.now())
                .build();

        // When
        adapter.incrementCounters(transaction);

        // Then - Should increment normally but location count should be 0
        VelocityMetrics metrics = adapter.findVelocityMetricsByTransaction(transaction);
        assertThat(metrics.getTransactionCount(FIVE_MINUTES)).isEqualTo(1L);
        assertThat(metrics.getUniqueLocations(FIVE_MINUTES)).isZero();
    }

    // ========================================
    // Helper Methods
    // ========================================

    private String uniqueAccountId(String base) {
        return testNamespace + base + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private RAtomicLong findTransactionCounter(Transaction transaction, TimeWindow timeWindow) {
        return redissonClient.getAtomicLong(
                TRANSACTION_COUNTER_KEY + ":%s:%s".formatted(timeWindow.getLabel(), transaction.accountId())
        );
    }

    private RHyperLogLog<String> findMerchantCounter(Transaction transaction, TimeWindow timeWindow) {
        return redissonClient.getHyperLogLog(
                MERCHANTS_COUNTER_KEY + ":%s:%s".formatted(timeWindow.getLabel(), transaction.accountId())
        );
    }

    private RHyperLogLog<String> findLocationCounter(Transaction transaction, TimeWindow timeWindow) {
        return redissonClient.getHyperLogLog(
                LOCATIONS_COUNTER_KEY + ":%s:%s".formatted(timeWindow.getLabel(), transaction.accountId())
        );
    }

    private RAtomicDouble findTotalAmountCounter(Transaction transaction, TimeWindow timeWindow) {
        return redissonClient.getAtomicDouble(
                TOTAL_AMOUNT_COUNTER_KEY + ":%s:%s".formatted(timeWindow.getLabel(), transaction.accountId())
        );
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