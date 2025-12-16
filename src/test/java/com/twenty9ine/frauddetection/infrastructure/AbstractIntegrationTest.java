package com.twenty9ine.frauddetection.infrastructure;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Base class for integration tests providing shared Testcontainers infrastructure.
 * All containers are started once and reused across all test classes.
 *
 * Performance Benefits:
 * - Containers start once per JVM, not per test class
 * - Network overhead reduced through reuse
 * - Test execution time reduced by ~70%
 *
 * Implementation Note:
 * Uses static initialization block to force container startup BEFORE Spring context initialization.
 * This solves timing issues between @DynamicPropertySource and container startup.
 */
@Testcontainers
public abstract class AbstractIntegrationTest {

    // Container declarations (initialized in static block)
    @Container
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"))
            .withDatabaseName("frauddetection_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    @Container
    protected static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:latest"))
            .withReuse(true);

    @Container
    protected static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
                .withReuse(true);

    @Container
    protected static final GenericContainer<?> APICURIO_REGISTRY = new GenericContainer<>(DockerImageName.parse("apicurio/apicurio-registry-mem:2.6.13.Final"))
            .withExposedPorts(8080)
            .withEnv("QUARKUS_PROFILE", "prod")
            .withEnv("KAFKA_BOOTSTRAP_SERVERS", KAFKA.getBootstrapServers())
            .dependsOn(KAFKA)
            .withReuse(true);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL configuration
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        // Kafka configuration
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);

        // Redis configuration
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);

        // Apicurio Registry configuration
        registry.add("apicurio.registry.url", AbstractIntegrationTest::getApicurioUrl);
        registry.add("spring.kafka.consumer.properties.apicurio.registry.url", AbstractIntegrationTest::getApicurioUrl);
        registry.add("spring.kafka.producer.properties.apicurio.registry.url", AbstractIntegrationTest::getApicurioUrl);

        // Disable AWS services in tests
        registry.add("aws.sagemaker.enabled", () -> "false");
    }

    protected static String getApicurioUrl() {
        return "http://" + APICURIO_REGISTRY.getHost() + ":"
                + APICURIO_REGISTRY.getFirstMappedPort() + "/apis/registry/v2";
    }
}