package com.twenty9ine.frauddetection.infrastructure.adapter.cache;

import com.twenty9ine.frauddetection.domain.valueobject.TimeWindow;
import com.twenty9ine.frauddetection.domain.valueobject.Transaction;
import com.twenty9ine.frauddetection.domain.valueobject.VelocityMetrics;
import com.twenty9ine.frauddetection.application.port.out.VelocityServicePort;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicDouble;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RHyperLogLog;
import org.redisson.api.RedissonClient;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

import static com.twenty9ine.frauddetection.domain.valueobject.TimeWindow.*;

@Component
@Slf4j
public class VelocityCounterAdapter implements VelocityServicePort {

    private final RedissonClient redissonClient;
    private final CacheManager cacheManager;

    public VelocityCounterAdapter(RedissonClient redissonClient, CacheManager cacheManager) {
        this.redissonClient = redissonClient;
        this.cacheManager = cacheManager;
    }

    @Override
    public VelocityMetrics findVelocityMetricsByTransaction(Transaction transaction) {
        Cache cache = getCache();
        VelocityMetrics cachedVelocityMetrics = extractVelocityMetricsByAccountId(transaction.accountId(), cache);

        if (cachedVelocityMetrics != null) {
            return cachedVelocityMetrics;
        }

        VelocityMetrics metrics = fetchFromRedis(transaction);
        updateCache(transaction.accountId(), cache, metrics);

        return metrics;
    }

    private static void updateCache(String accountId, Cache cache, VelocityMetrics metrics) {
        if (cache != null) {
            cache.put(accountId, metrics);
        }
    }

    private static VelocityMetrics extractVelocityMetricsByAccountId(String accountId, Cache cache) {
        return cache != null ? cache.get(accountId, VelocityMetrics.class) : null;
    }

    private Cache getCache() {
        return cacheManager.getCache("velocityMetrics");
    }

    private VelocityMetrics fetchFromRedis(Transaction transaction) {
        return VelocityMetrics.builder()
                              .transactionCounts(findTransactionCounts(transaction))
                              .totalAmounts(findTotalAmounts(transaction))
                              .uniqueMerchants(findMerchantCounts(transaction))
                              .uniqueLocations(findLocationCounts(transaction))
                              .build();
    }

    private Map<TimeWindow, Long> findTransactionCounts(Transaction transaction) {
        return Map.of(FIVE_MINUTES, findTransactionCounter(transaction, FIVE_MINUTES).get(),
                      ONE_HOUR, findTransactionCounter(transaction, ONE_HOUR).get(),
                      TWENTY_FOUR_HOURS, findTransactionCounter(transaction, TWENTY_FOUR_HOURS).get());
    }

    private Map<TimeWindow, BigDecimal> findTotalAmounts(Transaction transaction) {
        return Map.of(FIVE_MINUTES, BigDecimal.valueOf(findTotalAmountCounter(transaction, FIVE_MINUTES).get()),
                      ONE_HOUR, BigDecimal.valueOf(findTotalAmountCounter(transaction, ONE_HOUR).get()),
                      TWENTY_FOUR_HOURS, BigDecimal.valueOf(findTotalAmountCounter(transaction, TWENTY_FOUR_HOURS).get()));
    }

    private Map<TimeWindow, Long> findMerchantCounts(Transaction transaction) {
        return Map.of(FIVE_MINUTES, findMerchantCounter(transaction, FIVE_MINUTES).count(),
                      ONE_HOUR, findMerchantCounter(transaction, ONE_HOUR).count(),
                      TWENTY_FOUR_HOURS, findMerchantCounter(transaction, TWENTY_FOUR_HOURS).count());
    }

    private Map<TimeWindow, Long> findLocationCounts(Transaction transaction) {
        return Map.of(FIVE_MINUTES, findLocationCounter(transaction, FIVE_MINUTES).count(),
                      ONE_HOUR, findLocationCounter(transaction, ONE_HOUR).count(),
                      TWENTY_FOUR_HOURS, findLocationCounter(transaction, TWENTY_FOUR_HOURS).count());
    }

    @Override
    public void incrementCounters(Transaction transaction) {
        incrementTransactionCounters(transaction);
        incrementTotalAmounts(transaction);
        incrementMerchantCounters(transaction);
        incrementLocationCounters(transaction);

        evictCache(transaction.accountId());
    }

    private void incrementTotalAmounts(Transaction transaction) {
        incrementTotalAmount(transaction, FIVE_MINUTES);
        incrementTotalAmount(transaction, ONE_HOUR);
        incrementTotalAmount(transaction, TWENTY_FOUR_HOURS);
    }

    private void incrementTotalAmount(Transaction transaction, TimeWindow timeWindow) {
        RAtomicDouble totalAmount = findTotalAmountCounter(transaction, timeWindow);
        totalAmount.addAndGet(transaction.amount().value().doubleValue());
        totalAmount.expire(timeWindow.getDuration());
    }

    private RAtomicDouble findTotalAmountCounter(Transaction transaction, TimeWindow timeWindow) {
        return redissonClient.getAtomicDouble("velocity:amount:%s:%s".formatted(timeWindow.getLabel(), transaction.accountId()));
    }

    private void incrementMerchantCounters(Transaction transaction) {
        incrementMerchantCounter(transaction, FIVE_MINUTES);
        incrementMerchantCounter(transaction, ONE_HOUR);
        incrementMerchantCounter(transaction, TWENTY_FOUR_HOURS);
    }

    private void incrementMerchantCounter(Transaction transaction, TimeWindow timeWindow) {
        RHyperLogLog<String> merchantLog = findMerchantCounter(transaction, timeWindow);
        merchantLog.add(transaction.merchantId());
        merchantLog.expire(timeWindow.getDuration());
    }

    private RHyperLogLog<String> findMerchantCounter(Transaction transaction, TimeWindow timeWindow) {
        return redissonClient.getHyperLogLog("velocity:merchants:%s:%s".formatted(timeWindow.getLabel(), transaction.accountId()));
    }

    private void incrementTransactionCounters(Transaction transaction) {
        incrementTransactionCounter(transaction, FIVE_MINUTES);
        incrementTransactionCounter(transaction, ONE_HOUR);
        incrementTransactionCounter(transaction, TWENTY_FOUR_HOURS);
    }

    private void incrementTransactionCounter(Transaction transaction, TimeWindow timeWindow) {
        RAtomicLong counter = findTransactionCounter(transaction, timeWindow);
        counter.incrementAndGet();
        counter.expire(timeWindow.getDuration());
    }

    private RAtomicLong findTransactionCounter(Transaction transaction, TimeWindow timeWindow) {
        return redissonClient.getAtomicLong("velocity:%s:%s".formatted(timeWindow.getLabel(), transaction.accountId()));
    }

    private void incrementLocationCounters(Transaction transaction) {
        incrementLocationCounter(transaction, FIVE_MINUTES);
        incrementLocationCounter(transaction, ONE_HOUR);
        incrementLocationCounter(transaction, TWENTY_FOUR_HOURS);
    }

    private void incrementLocationCounter(Transaction transaction, TimeWindow timeWindow) {
        RHyperLogLog<String> locationLog = findLocationCounter(transaction, timeWindow);
        locationLog.add(transaction.location().toString());
        locationLog.expire(timeWindow.getDuration());
    }

    private RHyperLogLog<String> findLocationCounter(Transaction transaction, TimeWindow timeWindow) {
        return redissonClient.getHyperLogLog("velocity:locations:%s:%s".formatted(timeWindow.getLabel(), transaction.accountId()));
    }

    private void evictCache(String accountId) {
        Cache cache = getCache();

        if (cache != null) {
            cache.evict(accountId);
        }
    }
}
