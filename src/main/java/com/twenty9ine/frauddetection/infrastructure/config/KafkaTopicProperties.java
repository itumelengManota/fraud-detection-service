package com.twenty9ine.frauddetection.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "kafka.topics.transactions")
public class KafkaTopicProperties {
    String name;
    int concurrency;
    String groupId;
}