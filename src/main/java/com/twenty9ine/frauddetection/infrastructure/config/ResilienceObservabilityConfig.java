package com.twenty9ine.frauddetection.infrastructure.config;

import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedBulkheadMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedTimeLimiterMetrics;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bridges Resilience4j 2.3.x (built against Spring Boot 3) to Spring Boot 4's relocated
 * actuator APIs. Resilience4j's bundled {@code CircuitBreakersHealthIndicator} implements
 * the Spring Boot 3 {@code org.springframework.boot.actuate.health.HealthIndicator}
 * interface, which Spring Boot 4 no longer scans — the interface moved to
 * {@code org.springframework.boot.health.contributor.HealthIndicator}. Similarly, the
 * auto-configured metrics binders do not attach to the Spring Boot 4 Micrometer
 * {@link MeterRegistry} without explicit wiring.
 * <p>
 * This configuration re-binds the Resilience4j registries to the Spring Boot 4 actuator
 * and Micrometer infrastructure.
 */
@Configuration
public class ResilienceObservabilityConfig {

    @Bean
    public TaggedCircuitBreakerMetrics circuitBreakerMetrics(CircuitBreakerRegistry registry,
                                                             MeterRegistry meterRegistry) {
        TaggedCircuitBreakerMetrics metrics = TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry);
        metrics.bindTo(meterRegistry);
        return metrics;
    }

    @Bean
    public TaggedRetryMetrics retryMetrics(RetryRegistry registry, MeterRegistry meterRegistry) {
        TaggedRetryMetrics metrics = TaggedRetryMetrics.ofRetryRegistry(registry);
        metrics.bindTo(meterRegistry);
        return metrics;
    }

    @Bean
    public TaggedBulkheadMetrics bulkheadMetrics(BulkheadRegistry registry, MeterRegistry meterRegistry) {
        TaggedBulkheadMetrics metrics = TaggedBulkheadMetrics.ofBulkheadRegistry(registry);
        metrics.bindTo(meterRegistry);
        return metrics;
    }

    @Bean
    public TaggedTimeLimiterMetrics timeLimiterMetrics(TimeLimiterRegistry registry, MeterRegistry meterRegistry) {
        TaggedTimeLimiterMetrics metrics = TaggedTimeLimiterMetrics.ofTimeLimiterRegistry(registry);
        metrics.bindTo(meterRegistry);
        return metrics;
    }

    /**
     * Spring Boot 4–native health indicator for Resilience4j circuit breakers. Rolls every
     * registered breaker into a single {@code circuitBreakers} component: UP when all
     * breakers are {@code CLOSED} or {@code HALF_OPEN}; DOWN when any breaker is
     * {@code OPEN} or {@code FORCED_OPEN}.
     */
    @Bean
    public HealthIndicator circuitBreakersHealthIndicator(CircuitBreakerRegistry registry) {
        return () -> {
            Health.Builder builder = Health.up();
            boolean anyDown = false;
            for (CircuitBreaker cb : registry.getAllCircuitBreakers()) {
                CircuitBreaker.State state = cb.getState();
                builder.withDetail(cb.getName(), state.name());
                if (state == CircuitBreaker.State.OPEN || state == CircuitBreaker.State.FORCED_OPEN) {
                    anyDown = true;
                }
            }
            return (anyDown ? builder.status(Status.DOWN) : builder).build();
        };
    }
}
