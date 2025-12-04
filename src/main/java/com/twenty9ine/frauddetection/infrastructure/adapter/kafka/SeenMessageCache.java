package com.twenty9ine.frauddetection.infrastructure.adapter.kafka;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.twenty9ine.frauddetection.domain.valueobject.TransactionId;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
public class SeenMessageCache {

    private final Cache<UUID, Boolean> cache;

    //TODO: This cache mechanism is a simple in-memory solution. For a distributed system, consider using a distributed cache like Redis.
    public SeenMessageCache() {
        this.cache = Caffeine.newBuilder()
                .maximumSize(100_000)
                .expireAfterWrite(Duration.ofHours(24))
                .build();
    }

    public boolean hasProcessed(TransactionId transactionId) {
        return cache.getIfPresent(transactionId.toUUID()) != null;
    }

    public void markProcessed(TransactionId transactionId) {
        cache.put(transactionId.toUUID(), Boolean.TRUE);
    }
}
