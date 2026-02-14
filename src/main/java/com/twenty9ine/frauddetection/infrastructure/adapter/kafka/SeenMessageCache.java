package com.twenty9ine.frauddetection.infrastructure.adapter.kafka;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SeenMessageCache {

    private static final String SEEN_MESSAGE_KEY_PREFIX = "seen:transaction:";

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${spring.cache.redis.time-to-live:172800000}") // Default 48 hours
    private long ttlMillis;

    public void markProcessed(UUID transactionId) {
        redisTemplate.opsForValue().set(buildKey(transactionId), true, Duration.ofMillis(ttlMillis));
    }

    public boolean hasProcessed(UUID transactionId) {
        Boolean isKeyFound = (Boolean) redisTemplate.opsForValue().get(buildKey(transactionId));
        return Boolean.TRUE.equals(isKeyFound);
    }

    private static String buildKey(UUID transactionId) {
        return SEEN_MESSAGE_KEY_PREFIX + transactionId;
    }
}