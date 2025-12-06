package com.twenty9ine.frauddetection.infrastructure.adapter.kafka;

import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
public class SeenMessageCache {

    private static final String SEEN_MESSAGE_KEY_PREFIX = "seen:transaction:";
    private final RedissonClient redissonClient;
    private final Duration ttl;

    public SeenMessageCache(RedissonClient redissonClient,
                            @Value("${fraud-detection.transaction-event-consumer.idempotency.ttl-minutes}")
                            Duration ttl) {
        this.redissonClient = redissonClient;
        this.ttl = ttl;
    }

    public void markProcessed(UUID transactionId) {
        getRBucket(transactionId).set(true, ttl);
    }

    public boolean hasProcessed(UUID transactionId) {
        Boolean result = getRBucket(transactionId).get();
        return result != null && result;
    }

    private RBucket<Boolean> getRBucket(UUID transactionId) {
        return redissonClient.getBucket(SEEN_MESSAGE_KEY_PREFIX + transactionId);
    }
}

