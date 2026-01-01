package com.twenty9ine.frauddetection.infrastructure.adapter.account.config;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jdbc.JdbcRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;

/**
 * Test configuration for AccountServiceRestAdapter integration tests.
 * Enables Spring Boot auto-configuration but excludes database and Kafka related configs.
 */
@TestConfiguration
@EnableAutoConfiguration(exclude = {
    // Database auto-configurations
    DataSourceAutoConfiguration.class,
    JdbcRepositoriesAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class,
    DataSourceTransactionManagerAutoConfiguration.class,
    
    // Kafka auto-configuration (not needed for this test)
    KafkaAutoConfiguration.class,
    
    // Redis auto-configuration (using Caffeine for caching instead)
    org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class,
    org.redisson.spring.starter.RedissonAutoConfigurationV2.class
})
public class AccountServiceTestConfig {
}
