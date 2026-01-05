package com.twenty9ine.frauddetection.infrastructure.adapter.account.config;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;

/**
 * Test configuration for AccountServiceRestAdapter integration tests.
 * Enables Spring Boot auto-configuration but excludes database and Kafka related configs.
 */
@TestConfiguration
@EnableAutoConfiguration(exclude = {
    DataSourceAutoConfiguration.class,
    DataSourceTransactionManagerAutoConfiguration.class,
    KafkaAutoConfiguration.class
})
public class AccountServiceTestConfig {
}
