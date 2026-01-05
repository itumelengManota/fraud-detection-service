package com.twenty9ine.frauddetection.infrastructure.adapter.cache;

import com.twenty9ine.frauddetection.application.port.out.VelocityServicePort;
import com.twenty9ine.frauddetection.domain.valueobject.TimeWindow;
import com.twenty9ine.frauddetection.domain.valueobject.Transaction;
import com.twenty9ine.frauddetection.domain.valueobject.VelocityMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

import static com.twenty9ine.frauddetection.domain.valueobject.TimeWindow.*;

@RequiredArgsConstructor
@Component
@Slf4j
public class VelocityCounterAdapter implements VelocityServicePort {

    private static final String TOTAL_AMOUNT_KEY = "velocity:amount";
    private static final String MERCHANTS_KEY = "velocity:merchants";
    private static final String TRANSACTION_COUNTER_KEY = "velocity:transaction:counter";
    private static final String LOCATIONS_KEY = "velocity:locations";

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    @Cacheable(value = "velocityMetrics", key = "#transaction.accountId()")
    public VelocityMetrics findVelocityMetricsByTransaction(Transaction transaction) {
        return VelocityMetrics.builder()
                .transactionCounts(findTransactionCounts(transaction))
                .totalAmounts(findTotalAmounts(transaction))
                .uniqueMerchants(findMerchantCounts(transaction))
                .uniqueLocations(findLocationCounts(transaction))
                .build();
    }

    @Override
    @CacheEvict(value = "velocityMetrics", key = "#transaction.accountId()")
    public void incrementCounters(Transaction transaction) {
        incrementTransactionCounters(transaction);
        incrementTotalAmounts(transaction);
        incrementMerchantCounters(transaction);
        incrementLocationCounters(transaction);
    }

    private String buildKey(String prefix, TimeWindow window, String accountId) {
        return String.format("%s:%s:%s", prefix, window.getLabel(), accountId);
    }

    private Map<TimeWindow, Long> findTransactionCounts(Transaction transaction) {
        return Map.of(
                FIVE_MINUTES, getCounter(transaction, FIVE_MINUTES),
                ONE_HOUR, getCounter(transaction, ONE_HOUR),
                TWENTY_FOUR_HOURS, getCounter(transaction, TWENTY_FOUR_HOURS)
        );
    }

    private Long getCounter(Transaction transaction, TimeWindow window) {
        String key = buildKey(TRANSACTION_COUNTER_KEY, window, transaction.accountId());

        Object value = redisTemplate.opsForValue().get(key);
        return value != null ? ((Number) value).longValue() : 0L;
    }

    private void incrementTransactionCounters(Transaction transaction) {
        incrementCounter(transaction, FIVE_MINUTES);
        incrementCounter(transaction, ONE_HOUR);
        incrementCounter(transaction, TWENTY_FOUR_HOURS);
    }

    private void incrementCounter(Transaction transaction, TimeWindow window) {
        String key = buildKey(TRANSACTION_COUNTER_KEY, window, transaction.accountId());

        redisTemplate.opsForValue().increment(key, 1);
        redisTemplate.expire(key, window.getDuration());
    }

    private Map<TimeWindow, BigDecimal> findTotalAmounts(Transaction transaction) {
        return Map.of(
                FIVE_MINUTES, getTotalAmount(transaction, FIVE_MINUTES),
                ONE_HOUR, getTotalAmount(transaction, ONE_HOUR),
                TWENTY_FOUR_HOURS, getTotalAmount(transaction, TWENTY_FOUR_HOURS)
        );
    }

    private BigDecimal getTotalAmount(Transaction transaction, TimeWindow window) {
        String key = buildKey(TOTAL_AMOUNT_KEY, window, transaction.accountId());
        Object value = redisTemplate.opsForValue().get(key);

        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        } else if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }

        return BigDecimal.ZERO;
    }

    private void incrementTotalAmounts(Transaction transaction) {
        incrementAmount(transaction, FIVE_MINUTES);
        incrementAmount(transaction, ONE_HOUR);
        incrementAmount(transaction, TWENTY_FOUR_HOURS);
    }

    private void incrementAmount(Transaction transaction, TimeWindow window) {
        String key = buildKey(TOTAL_AMOUNT_KEY, window, transaction.accountId());
        double amount = transaction.amount().value().doubleValue();

        redisTemplate.opsForValue().increment(key, amount);
        redisTemplate.expire(key, window.getDuration());
    }

    private Map<TimeWindow, Long> findMerchantCounts(Transaction transaction) {
        return Map.of(
                FIVE_MINUTES, getHyperLogLogCount(transaction, FIVE_MINUTES, MERCHANTS_KEY),
                ONE_HOUR, getHyperLogLogCount(transaction, ONE_HOUR, MERCHANTS_KEY),
                TWENTY_FOUR_HOURS, getHyperLogLogCount(transaction, TWENTY_FOUR_HOURS, MERCHANTS_KEY)
        );
    }

    private void incrementMerchantCounters(Transaction transaction) {
        addToHyperLogLog(transaction, FIVE_MINUTES, MERCHANTS_KEY, transaction.merchant().id().merchantId());
        addToHyperLogLog(transaction, ONE_HOUR, MERCHANTS_KEY, transaction.merchant().id().merchantId());
        addToHyperLogLog(transaction, TWENTY_FOUR_HOURS, MERCHANTS_KEY, transaction.merchant().id().merchantId());
    }

    private Map<TimeWindow, Long> findLocationCounts(Transaction transaction) {
        return Map.of(
                FIVE_MINUTES, getHyperLogLogCount(transaction, FIVE_MINUTES, LOCATIONS_KEY),
                ONE_HOUR, getHyperLogLogCount(transaction, ONE_HOUR, LOCATIONS_KEY),
                TWENTY_FOUR_HOURS, getHyperLogLogCount(transaction, TWENTY_FOUR_HOURS, LOCATIONS_KEY)
        );
    }

    private void incrementLocationCounters(Transaction transaction) {
        String location = transaction.location().toString();

        addToHyperLogLog(transaction, FIVE_MINUTES, LOCATIONS_KEY, location);
        addToHyperLogLog(transaction, ONE_HOUR, LOCATIONS_KEY, location);
        addToHyperLogLog(transaction, TWENTY_FOUR_HOURS, LOCATIONS_KEY, location);
    }

    private Long getHyperLogLogCount(Transaction transaction, TimeWindow window, String prefix) {
        String key = buildKey(prefix, window, transaction.accountId());
        return redisTemplate.opsForHyperLogLog().size(key);
    }

    private void addToHyperLogLog(Transaction transaction, TimeWindow window, String prefix, String value) {
        String key = buildKey(prefix, window, transaction.accountId());

        redisTemplate.opsForHyperLogLog().add(key, value);
        redisTemplate.expire(key, window.getDuration());
    }
}