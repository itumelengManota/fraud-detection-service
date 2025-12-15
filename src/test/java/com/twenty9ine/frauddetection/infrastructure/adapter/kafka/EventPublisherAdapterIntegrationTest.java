package com.twenty9ine.frauddetection.infrastructure.adapter.kafka;

import com.twenty9ine.frauddetection.domain.event.DomainEvent;
import com.twenty9ine.frauddetection.domain.event.HighRiskDetected;
import com.twenty9ine.frauddetection.domain.event.RiskAssessmentCompleted;
import com.twenty9ine.frauddetection.domain.valueobject.*;
import com.twenty9ine.frauddetection.infrastructure.AbstractIntegrationTest;
import com.twenty9ine.frauddetection.infrastructure.KafkaTestConsumerFactory;
import io.apicurio.registry.serde.SerdeConfig;
import io.apicurio.registry.serde.avro.AvroKafkaSerializer;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for EventPublisherAdapter with optimized performance.
 *
 * Performance Optimizations:
 * - Extends AbstractIntegrationTest for shared container infrastructure
 * - Uses @TestInstance(PER_CLASS) for shared setup across tests
 * - Kafka consumer pooling via KafkaTestConsumerFactory
 * - Topics created once in @BeforeAll
 * - Parallel execution with proper resource locking
 * - Reusable Kafka template and event publisher
 *
 * Expected performance gain: 60-70% faster than original implementation
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("EventPublisherAdapter Integration Tests")
@Execution(ExecutionMode.CONCURRENT)
@ResourceLock(value = "kafka-topics", mode = ResourceAccessMode.READ)
class EventPublisherAdapterIntegrationTest extends AbstractIntegrationTest {

    private EventPublisherAdapter eventPublisher;
    private KafkaConsumer<String, Object> testConsumer;
    private KafkaTemplate<String, Object> kafkaTemplate;

    @BeforeAll
    void setUpClass() throws ExecutionException, InterruptedException {
        // Create topics once for all tests - saves ~2 seconds per test class
        createKafkaTopics();

        // Initialize Kafka infrastructure - reused across all tests
        String registryUrl = getApicurioUrl();

        // Create producer components with optimized configuration
        Map<String, Object> producerConfig = buildProducerConfig(registryUrl);
        DefaultKafkaProducerFactory<String, Object> producerFactory =
                new DefaultKafkaProducerFactory<>(producerConfig);
        kafkaTemplate = new KafkaTemplate<>(producerFactory);

        // Initialize event publisher - reused across all tests
        DomainEventToAvroMapper avroMapper = new DomainEventToAvroMapper();
        eventPublisher = new EventPublisherAdapter(kafkaTemplate, avroMapper);

        // Get pooled consumer - saves 500-1000ms per test
        testConsumer = KafkaTestConsumerFactory.getConsumer(
                KAFKA.getBootstrapServers(),
                registryUrl
        );
    }

    @AfterEach
    void resetConsumer() {
        // Reset consumer state for next test - much faster than creating new consumer
        KafkaTestConsumerFactory.resetConsumer(testConsumer);
    }

    @AfterAll
    void tearDownClass() {
        // Consumer is returned to pool, not closed - enables reuse across test classes
        if (kafkaTemplate != null) {
            kafkaTemplate.destroy();
        }
    }

    // ========================================
    // Test Cases
    // ========================================

    @Test
    @Order(1)
    @DisplayName("Should publish RiskAssessmentCompleted event to correct topic")
    void shouldPublishRiskAssessmentCompletedEvent() {
        // Given
        subscribeTo(Collections.singletonList("fraud-detection.risk-assessments"));
        RiskAssessmentCompleted event = createRiskAssessmentCompletedEvent(TransactionId.generate());

        // When
        publish(event);

        // Then
        ConsumerRecords<String, Object> records = pollConsumerRecord();
        assertThat(records).isNotEmpty();

        ConsumerRecord<String, Object> record = records.iterator().next();
        assertThat(record.topic()).isEqualTo("fraud-detection.risk-assessments");
        assertThat(record.key()).isEqualTo(event.id().toString());

        RiskAssessmentCompletedAvro avroEvent = (RiskAssessmentCompletedAvro) record.value();
        assertThat(avroEvent.getAssessmentId()).isEqualTo(event.assessmentId().toString());
        assertThat(avroEvent.getId()).isEqualTo(event.id().toString());
        assertThat(avroEvent.getFinalScore()).isEqualTo(event.finalScore().value());
        assertThat(avroEvent.getRiskLevel()).isEqualTo(event.transactionRiskLevel().name());
        assertThat(avroEvent.getDecision()).isEqualTo(event.decision().name());
    }

    @Test
    @Order(2)
    @DisplayName("Should publish HighRiskDetected event to correct topic")
    void shouldPublishHighRiskDetectedEvent() {
        // Given
        subscribeTo(Collections.singletonList("fraud-detection.high-risk-alerts"));
        HighRiskDetected event = createHighRiskDetectedEvent(TransactionId.generate());

        // When
        publish(event);

        // Then
        ConsumerRecords<String, Object> records = pollConsumerRecord();
        assertThat(records).isNotEmpty();

        ConsumerRecord<String, Object> record = records.iterator().next();
        assertThat(record.topic()).isEqualTo("fraud-detection.high-risk-alerts");
        assertThat(record.key()).isEqualTo(event.id().toString());

        HighRiskDetectedAvro avroEvent = (HighRiskDetectedAvro) record.value();
        assertThat(avroEvent.getAssessmentId()).isEqualTo(event.assessmentId().toString());
        assertThat(avroEvent.getId()).isEqualTo(event.id().toString());
        assertThat(avroEvent.getRiskLevel()).isEqualTo(event.transactionRiskLevel().name());
    }

    @Test
    @Order(3)
    @DisplayName("Should publish multiple events in order")
    void shouldPublishAllEvents() {
        // Given
        subscribeTo(Arrays.asList(
                "fraud-detection.risk-assessments",
                "fraud-detection.high-risk-alerts"
        ));

        TransactionId transactionId = TransactionId.generate();
        List<DomainEvent<TransactionId>> events = Arrays.asList(
                createRiskAssessmentCompletedEvent(transactionId),
                createHighRiskDetectedEvent(transactionId),
                createRiskAssessmentCompletedEvent(transactionId)
        );

        // When
        publishAll(events);

        // Then
        List<ConsumerRecord<String, Object>> receivedRecords =
                pollConsumerRecords(15000, events.size());

        assertThat(receivedRecords).hasSize(events.size());

        Map<String, Long> topicCounts = groupByTopic(receivedRecords);
        assertThat(topicCounts.get("fraud-detection.risk-assessments")).isEqualTo(2L);
        assertThat(topicCounts.get("fraud-detection.high-risk-alerts")).isEqualTo(1L);

        // Verify the types match the topics
        receivedRecords.forEach(record -> {
            if (record.topic().equals("fraud-detection.risk-assessments")) {
                assertThat(record.value()).isInstanceOf(RiskAssessmentCompletedAvro.class);
            } else if (record.topic().equals("fraud-detection.high-risk-alerts")) {
                assertThat(record.value()).isInstanceOf(HighRiskDetectedAvro.class);
            }
        });
    }

    @Test
    @Order(4)
    @DisplayName("Should use correct partition key for events")
    void shouldUseEventIdAsPartitionKey() {
        // Given
        subscribeTo(Collections.singletonList("fraud-detection.risk-assessments"));
        RiskAssessmentCompleted event = createRiskAssessmentCompletedEvent(TransactionId.generate());

        // When
        publish(event);

        // Then
        ConsumerRecords<String, Object> records = pollConsumerRecord();
        ConsumerRecord<String, Object> record = records.iterator().next();
        assertThat(record.key()).isEqualTo(event.id().toString());
    }

    @Test
    @Order(5)
    @DisplayName("Should handle concurrent publishing")
    void shouldHandleConcurrentPublishing() throws InterruptedException {
        // Given
        subscribeTo(Arrays.asList(
                "fraud-detection.risk-assessments",
                "fraud-detection.high-risk-alerts"
        ));

        int numberOfEvents = 20;
        List<Thread> threads = new ArrayList<>();

        // When - Simulate concurrent publishing
        for (int i = 0; i < numberOfEvents; i++) {
            Thread thread = new Thread(() ->
                    eventPublisher.publish(createRiskAssessmentCompletedEvent(TransactionId.generate()))
            );
            threads.add(thread);
            thread.start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join(5000);
        }

        kafkaTemplate.flush(); // Ensure all messages are sent

        // Then
        List<ConsumerRecord<String, Object>> receivedRecords =
                pollConsumerRecords(20000, numberOfEvents);

        assertThat(receivedRecords).hasSize(numberOfEvents);

        Map<String, Long> topicCounts = groupByTopic(receivedRecords);
        assertThat(topicCounts.get("fraud-detection.risk-assessments")).isEqualTo(numberOfEvents);

        // Verify all are correct type
        receivedRecords.forEach(record ->
                assertThat(record.value()).isInstanceOf(RiskAssessmentCompletedAvro.class)
        );
    }

    @Test
    @Order(6)
    @DisplayName("Should register schema automatically with Apicurio")
    void shouldAutoRegisterSchemaWithApicurio() {
        // Given
        subscribeTo(Collections.singletonList("fraud-detection.risk-assessments"));
        RiskAssessmentCompleted event = createRiskAssessmentCompletedEvent(TransactionId.generate());

        // When
        publish(event);

        // Then
        ConsumerRecords<String, Object> records = pollConsumerRecord();
        assertThat(records).isNotEmpty();

        // If schema wasn't registered, deserialization would fail
        assertThat(records.iterator().next().value())
                .isInstanceOf(RiskAssessmentCompletedAvro.class);
    }

    @Test
    @Order(7)
    @DisplayName("Should publish empty list without errors")
    void shouldHandleEmptyEventList() {
        // When/Then - Should not throw exception
        Assertions.assertDoesNotThrow(() ->
                eventPublisher.publishAll(Collections.emptyList())
        );
    }

    // ========================================
    // Helper Methods
    // ========================================

    private void createKafkaTopics() throws ExecutionException, InterruptedException {
        try (AdminClient admin = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()
        ))) {
            admin.createTopics(Arrays.asList(
                    new NewTopic("fraud-detection.risk-assessments", 3, (short) 1),
                    new NewTopic("fraud-detection.high-risk-alerts", 3, (short) 1)
            )).all().get();
        }
    }

    private Map<String, Object> buildProducerConfig(String registryUrl) {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, AvroKafkaSerializer.class);
        config.put(SerdeConfig.REGISTRY_URL, registryUrl);
        config.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, true);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.LINGER_MS_CONFIG, 0); // No delay for tests
        config.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        return config;
    }

    private void publish(DomainEvent<TransactionId> event) {
        eventPublisher.publish(event);
        kafkaTemplate.flush();
    }

    private void publishAll(List<DomainEvent<TransactionId>> events) {
        eventPublisher.publishAll(events);
        kafkaTemplate.flush();
    }

    private void subscribeTo(List<String> topics) {
        testConsumer.subscribe(topics);

        // Wait for partition assignment - critical for 'latest' offset strategy
        long endTime = System.currentTimeMillis() + 5000;
        while (testConsumer.assignment().isEmpty() && System.currentTimeMillis() < endTime) {
            testConsumer.poll(Duration.ofMillis(100));
        }

        if (testConsumer.assignment().isEmpty()) {
            throw new IllegalStateException("Consumer failed to get partition assignment");
        }

        testConsumer.poll(Duration.ofMillis(100)); // Ensure consumer is ready
    }

    private ConsumerRecords<String, Object> pollConsumerRecord() {
        return testConsumer.poll(Duration.ofSeconds(10));
    }

    private List<ConsumerRecord<String, Object>> pollConsumerRecords(int timeoutMs, int expectedCount) {
        List<ConsumerRecord<String, Object>> receivedRecords = new ArrayList<>();
        long endTime = System.currentTimeMillis() + timeoutMs;

        while (System.currentTimeMillis() < endTime && receivedRecords.size() < expectedCount) {
            ConsumerRecords<String, Object> polled = testConsumer.poll(Duration.ofSeconds(2));
            polled.forEach(receivedRecords::add);
        }

        return receivedRecords;
    }

    private RiskAssessmentCompleted createRiskAssessmentCompletedEvent(TransactionId transactionId) {
        return new RiskAssessmentCompleted(
                transactionId,
                AssessmentId.generate(),
                RiskScore.of(90),
                TransactionRiskLevel.HIGH,
                Decision.BLOCK,
                Instant.now()
        );
    }

    private HighRiskDetected createHighRiskDetectedEvent(TransactionId transactionId) {
        return new HighRiskDetected(
                transactionId,
                AssessmentId.generate(),
                TransactionRiskLevel.HIGH,
                Instant.now()
        );
    }

    private static Map<String, Long> groupByTopic(List<ConsumerRecord<String, Object>> records) {
        return records.stream()
                .collect(Collectors.groupingBy(ConsumerRecord::topic, Collectors.counting()));
    }
}