package com.twenty9ine.frauddetection.infrastructure.adapter.cache;

import com.twenty9ine.frauddetection.domain.valueobject.Location;
import com.twenty9ine.frauddetection.domain.valueobject.VelocityMetrics;
import com.twenty9ine.frauddetection.application.port.VelocityServicePort;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RHyperLogLog;
import org.redisson.api.RedissonClient;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

@Component
@Slf4j
public class VelocityCounterAdapter implements VelocityServicePort {

    private final RedissonClient redissonClient;
    private final CacheManager cacheManager;

    public VelocityCounterAdapter(
            RedissonClient redissonClient,
            CacheManager cacheManager) {
        this.redissonClient = redissonClient;
        this.cacheManager = cacheManager;
    }

    @Override
    public VelocityMetrics getVelocity(String accountId) {
        Cache cache = cacheManager.getCache("velocityMetrics");
        VelocityMetrics cached = cache != null ?
                cache.get(accountId, VelocityMetrics.class) : null;

        if (cached != null) {
            return cached;
        }

        VelocityMetrics metrics = fetchFromRedis(accountId);

        if (cache != null) {
            cache.put(accountId, metrics);
        }

        return metrics;
    }

private VelocityMetrics fetchFromRedis(String accountId) {
    RAtomicLong counter5min = redissonClient.getAtomicLong(
            "velocity:5min:" + accountId
    );

    RAtomicLong counter1hour = redissonClient.getAtomicLong(
            "velocity:1hour:" + accountId
    );

    RAtomicLong counter24hour = redissonClient.getAtomicLong(
            "velocity:24hour:" + accountId
    );

    RHyperLogLog<String> locationLog = redissonClient.getHyperLogLog(
            "velocity:locations:" + accountId
    );

    return VelocityMetrics.builder()
            .transactionCounts(Map.of(
                VelocityMetrics.FIVE_MINUTES, counter5min.get(),
                VelocityMetrics.ONE_HOUR, counter1hour.get(),
                VelocityMetrics.TWENTY_FOUR_HOURS, counter24hour.get()
            ))
            .totalAmounts(Map.of(
                VelocityMetrics.FIVE_MINUTES, BigDecimal.ZERO,
                VelocityMetrics.ONE_HOUR, BigDecimal.ZERO,
                VelocityMetrics.TWENTY_FOUR_HOURS, BigDecimal.ZERO
            ))
            .uniqueMerchants(Map.of(
                VelocityMetrics.FIVE_MINUTES, 0,
                VelocityMetrics.ONE_HOUR, 0,
                VelocityMetrics.TWENTY_FOUR_HOURS, 0
            ))
            .uniqueLocations(Map.of(
                VelocityMetrics.FIVE_MINUTES, locationLog.count(),
                VelocityMetrics.ONE_HOUR, locationLog.count(),
                VelocityMetrics.TWENTY_FOUR_HOURS, locationLog.count()
            ))
            .build();
}

    @Override
    public void incrementCounters(String accountId, Location location) {
        RAtomicLong counter5min = redissonClient.getAtomicLong(
                "velocity:5min:" + accountId
        );
        counter5min.incrementAndGet();
        counter5min.expire(Duration.ofMinutes(5));

        RAtomicLong counter1hour = redissonClient.getAtomicLong(
                "velocity:1hour:" + accountId
        );
        counter1hour.incrementAndGet();
        counter1hour.expire(Duration.ofHours(1));

        RAtomicLong counter24hour = redissonClient.getAtomicLong(
                "velocity:24hour:" + accountId
        );
        counter24hour.incrementAndGet();
        counter24hour.expire(Duration.ofDays(1));

        RHyperLogLog<String> locationLog = redissonClient.getHyperLogLog(
                "velocity:locations:" + accountId
        );
        locationLog.add(location.toString());
        locationLog.expire(Duration.ofDays(1));

        Cache cache = cacheManager.getCache("velocityMetrics");
        if (cache != null) {
            cache.evict(accountId);
        }
    }
}
