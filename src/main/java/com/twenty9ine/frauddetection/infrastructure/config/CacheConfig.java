package com.twenty9ine.frauddetection.infrastructure.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    @Primary
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("mlPredictions", "ruleConfigs", "merchantData", "velocityMetrics", "accountProfiles");

        cacheManager.setCaffeine(Caffeine.newBuilder().maximumSize(10_000)   //TODO: externalize to properties
                                                      .expireAfterWrite(Duration.ofMinutes(5))   //TODO: externalize to properties
                                                      .recordStats());

        return cacheManager;
    }
}
