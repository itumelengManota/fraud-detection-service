package com.twenty9ine.frauddetection.infrastructure.adapter.kafka;

import org.junit.jupiter.api.*;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@DataRedisTest
@Testcontainers
@Import({SeenMessageCache.class, SeenMessageCacheIntegrationTest.RedisTestConfig.class})
@Execution(ExecutionMode.CONCURRENT)
@ResourceLock("redis")
class SeenMessageCacheIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .withReuse(true);

    @DynamicPropertySource
    static void registerRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @TestConfiguration
    static class RedisTestConfig {
        @Bean
        public RedissonClient redissonClient() {
            Config config = new Config();
            config.useSingleServer().setAddress("redis://" + redis.getHost() + ":" + redis.getFirstMappedPort());
            return Redisson.create(config);
        }
    }

    @Autowired
    private SeenMessageCache seenMessageCache;

    @Autowired
    private RedissonClient redissonClient;

    private final Duration ttl = Duration.ofSeconds(10);

    @BeforeEach
    void setUp() {
        seenMessageCache = new SeenMessageCache(redissonClient, ttl);
        cleanupRedis();
    }

    @AfterEach
    void tearDown() {
        cleanupRedis();
    }

    private void cleanupRedis() {
        redissonClient.getKeys().flushall();
    }

    @Test
    @DisplayName("Should mark message as processed and verify it exists in cache")
    void shouldMarkMessageAsProcessed() {
        // Given
        UUID transactionId = UUID.randomUUID();

        // When
        seenMessageCache.markProcessed(transactionId);

        // Then
        assertThat(seenMessageCache.hasProcessed(transactionId))
                .as("Transaction should be marked as processed")
                .isTrue();
    }

    @Test
    @DisplayName("Should return false for unprocessed message")
    void shouldReturnFalseForUnprocessedMessage() {
        // Given
        UUID transactionId = UUID.randomUUID();

        // When & Then
        assertThat(seenMessageCache.hasProcessed(transactionId))
                .as("Unprocessed transaction should return false")
                .isFalse();
    }

    @Test
    @DisplayName("Should handle multiple different transactions")
    void shouldHandleMultipleTransactions() {
        // Given
        UUID transaction1 = UUID.randomUUID();
        UUID transaction2 = UUID.randomUUID();
        UUID transaction3 = UUID.randomUUID();

        // When
        seenMessageCache.markProcessed(transaction1);
        seenMessageCache.markProcessed(transaction2);

        // Then
        assertThat(seenMessageCache.hasProcessed(transaction1))
                .as("First transaction should be processed")
                .isTrue();
        assertThat(seenMessageCache.hasProcessed(transaction2))
                .as("Second transaction should be processed")
                .isTrue();
        assertThat(seenMessageCache.hasProcessed(transaction3))
                .as("Third transaction should not be processed")
                .isFalse();
    }

    @Test
    @DisplayName("Should expire entries after TTL")
    void shouldExpireEntriesAfterTtl() {
        // Given
        UUID transactionId = UUID.randomUUID();
        seenMessageCache.markProcessed(transactionId);

        // Verify initially present
        assertThat(seenMessageCache.hasProcessed(transactionId))
                .as("Transaction should be initially present")
                .isTrue();

        // When - Wait for expiration (TTL + buffer)
        await()
                .pollDelay(Duration.ofSeconds(1).plus(ttl)) //1s buffer over TTL
                .atMost(Duration.ofSeconds(10).plus(ttl))  //10s buffer
                .untilAsserted(() ->
                        assertThat(seenMessageCache.hasProcessed(transactionId))
                                .as("Transaction should expire after TTL")
                                .isFalse()
                );
    }

    @Test
    @DisplayName("Should handle same transaction ID marked multiple times")
    void shouldHandleIdempotentMarking() {
        // Given
        UUID transactionId = UUID.randomUUID();

        // When
        seenMessageCache.markProcessed(transactionId);
        seenMessageCache.markProcessed(transactionId);
        seenMessageCache.markProcessed(transactionId);

        // Then
        assertThat(seenMessageCache.hasProcessed(transactionId))
                .as("Transaction should remain marked as processed")
                .isTrue();
    }

    @Test
    @DisplayName("Should handle null transaction ID gracefully")
    void shouldHandleNullTransactionId() {
        // When & Then
        Assertions.assertDoesNotThrow(() -> {
            seenMessageCache.markProcessed(null);
            seenMessageCache.hasProcessed(null);
        }, "Should handle null without throwing exception");
    }

    @Test
    @DisplayName("Should persist entries across multiple checks")
    void shouldPersistEntriesAcrossMultipleChecks() {
        // Given
        UUID transactionId = UUID.randomUUID();
        seenMessageCache.markProcessed(transactionId);

        // When & Then - Check multiple times
        for (int i = 0; i < 10; i++) {
            assertThat(seenMessageCache.hasProcessed(transactionId))
                    .as("Transaction should remain processed on check #" + (i + 1))
                    .isTrue();
        }
    }

    @Test
    @DisplayName("Should handle high volume of transaction IDs")
    void shouldHandleHighVolumeOfTransactions() {
        // Given
        int transactionCount = 1000;
        UUID[] transactionIds = new UUID[transactionCount];

        // When - Mark many transactions
        for (int i = 0; i < transactionCount; i++) {
            transactionIds[i] = UUID.randomUUID();
            seenMessageCache.markProcessed(transactionIds[i]);
        }

        // Then - Verify all are present
        for (UUID transactionId : transactionIds) {
            assertThat(seenMessageCache.hasProcessed(transactionId))
                    .as("Transaction " + transactionId + " should be processed")
                    .isTrue();
        }
    }

    @Test
    @DisplayName("Should verify Redis connection is working")
    void shouldVerifyRedisConnectionIsWorking() {
        // When & Then
        assertThat(redissonClient.getConfig().isClusterConfig())
                .as("Redis should be accessible")
                .isFalse();
        assertThat(redis.isRunning())
                .as("Redis container should be running")
                .isTrue();
    }
}