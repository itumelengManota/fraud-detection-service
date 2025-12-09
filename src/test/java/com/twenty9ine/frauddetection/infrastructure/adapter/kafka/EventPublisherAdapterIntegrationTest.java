package com.twenty9ine.frauddetection.infrastructure.adapter.kafka;

import com.twenty9ine.frauddetection.domain.event.DomainEvent;
import com.twenty9ine.frauddetection.domain.event.HighRiskDetected;
import com.twenty9ine.frauddetection.domain.event.RiskAssessmentCompleted;
import com.twenty9ine.frauddetection.domain.valueobject.*;
import io.apicurio.registry.serde.SerdeConfig;
import io.apicurio.registry.serde.avro.AvroKafkaDeserializer;
import io.apicurio.registry.serde.avro.AvroKafkaSerializer;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.*;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EventPublisherAdapterIntegrationTest {

    private static final Network NETWORK = Network.newNetwork();

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"))
            .withNetwork(NETWORK)
            .withNetworkAliases("kafka");

    @Container
    static GenericContainer<?> apicurioRegistry = new GenericContainer<>(
            DockerImageName.parse("apicurio/apicurio-registry-mem:2.6.13.Final"))
            .withNetwork(NETWORK)
            .withNetworkAliases("apicurio")
            .withExposedPorts(8080)
            .withEnv("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092");

    private EventPublisherAdapter eventPublisher;
    private KafkaConsumer<String, Object> testConsumer;
    private KafkaTemplate<String, Object> kafkaTemplate;
    private String registryUrl;

    @BeforeAll
    static void setupTopics() {
        // Create topics with multiple partitions
        try (var admin = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()
        ))) {
            admin.createTopics(Arrays.asList(
                    new NewTopic("fraud-detection.risk-assessments", 3, (short) 1),
                    new NewTopic("fraud-detection.high-risk-alerts", 3, (short) 1)
            )).all().get();
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    void setUp() {
        registryUrl = buildApicurioRegisterUrl();

        // Create producer components
        Map<String, Object> producerConfig = new HashMap<>();
        producerConfig.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        producerConfig.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerConfig.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, AvroKafkaSerializer.class);
        producerConfig.put(SerdeConfig.REGISTRY_URL, registryUrl);
        producerConfig.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, true);
        producerConfig.put(ProducerConfig.ACKS_CONFIG, "all");

        DefaultKafkaProducerFactory<String, Object> producerFactory = new DefaultKafkaProducerFactory<>(producerConfig);
        kafkaTemplate = new KafkaTemplate<>(producerFactory);

        DomainEventToAvroMapper avroMapper = new DomainEventToAvroMapper();
        eventPublisher = new EventPublisherAdapter(kafkaTemplate, avroMapper);

        // Create test consumer
        testConsumer = createTestConsumer();
    }

    private KafkaConsumer<String, Object> createTestConsumer() {
        Map<String, Object> consumerConfig = new HashMap<>();
        consumerConfig.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + UUID.randomUUID());
        consumerConfig.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerConfig.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, AvroKafkaDeserializer.class);
        consumerConfig.put(SerdeConfig.REGISTRY_URL, registryUrl);
        consumerConfig.put("apicurio.registry.use-specific-avro-reader", true);
        consumerConfig.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        consumerConfig.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);

        return new KafkaConsumer<>(consumerConfig);
    }

    private static String buildApicurioRegisterUrl() {
        return "http://" + apicurioRegistry.getHost() + ":" + apicurioRegistry.getMappedPort(8080) + "/apis/registry/v2";
    }

    @AfterEach
    void tearDown() {
        if (testConsumer != null) {
            testConsumer.close();
        }
    }

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
        ConsumerRecords<String, Object> records = poolConsumerRecord();

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
        ConsumerRecords<String, Object> records = poolConsumerRecord();

        assertThat(records).isNotEmpty();
        ConsumerRecord<String, Object> record = records.iterator().next();

        assertThat(record.topic()).isEqualTo("fraud-detection.high-risk-alerts");
        assertThat(record.key()).isEqualTo(event.id().toString());

        HighRiskDetectedAvro avroEvent = (HighRiskDetectedAvro) record.value();
        assertThat(avroEvent.getAssessmentId()).isEqualTo(event.assessmentId().toString());
        assertThat(avroEvent.getId()).isEqualTo(event.id().toString());
        assertThat(avroEvent.getRiskLevel()).isEqualTo(event.transactionRiskLevel().name());
    }

    private ConsumerRecords<String, Object> poolConsumerRecord() {
        return testConsumer.poll(Duration.ofSeconds(10));
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
        List<ConsumerRecord<String, Object>> receivedRecords = poolConsumerRecords(15000, events.size());

        assertThat(receivedRecords).hasSize(events.size());

        Map<String, Long> topicCounts = groupByTopic(receivedRecords);

        // Verify correct distribution
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
        //TODO: Is this really testing partition key usage?
        // When
        publish(event);

        // Then
        ConsumerRecords<String, Object> records = poolConsumerRecord();
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

        // When
        for (int i = 0; i < numberOfEvents; i++) {
            Thread thread = new Thread(() -> {
                eventPublisher.publish(createRiskAssessmentCompletedEvent(TransactionId.generate()));
            });
            threads.add(thread);
            thread.start();
        }

        kafkaTemplate.flush(); // Ensure message is sent

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join(5000);
        }

        // Then
        List<ConsumerRecord<String, Object>> receivedRecords = poolConsumerRecords(20000, numberOfEvents);

        assertThat(receivedRecords).hasSize(numberOfEvents);

        Map<String, Long> topicCounts = groupByTopic(receivedRecords);
        assertThat(topicCounts.get("fraud-detection.risk-assessments")).isEqualTo(numberOfEvents);

        // Verify the types match the topics
        receivedRecords.forEach(record -> {
            if (record.topic().equals("fraud-detection.risk-assessments")) {
                assertThat(record.value()).isInstanceOf(RiskAssessmentCompletedAvro.class);
            }
        });
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
        ConsumerRecords<String, Object> records = poolConsumerRecord();

        assertThat(records).isNotEmpty();
        // If schema wasn't registered, deserialization would fail
        assertThat(records.iterator().next().value()).isInstanceOf(RiskAssessmentCompletedAvro.class);
    }

    @Test
    @Order(7)
    @DisplayName("Should publish empty list without errors")
    void shouldHandleEmptyEventList() {
        // When/Then
        Assertions.assertDoesNotThrow(() -> eventPublisher.publishAll(Collections.emptyList()));
    }


    private void publish(DomainEvent<TransactionId> event) {
        eventPublisher.publish(event);
        kafkaTemplate.flush(); // Ensure message is sent
    }

    private void publishAll(List<DomainEvent<TransactionId>> events) {
        eventPublisher.publishAll(events);
        kafkaTemplate.flush(); // Ensure message is sent
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

        // One more poll to ensure consumer is ready
        testConsumer.poll(Duration.ofMillis(100));
    }
    // Helper methods

    private RiskAssessmentCompleted createRiskAssessmentCompletedEvent(TransactionId transactionId) {
        return new RiskAssessmentCompleted(
                transactionId, AssessmentId.generate(),
                RiskScore.of(90),
                TransactionRiskLevel.HIGH,
                Decision.BLOCK,
                Instant.now()
        );
    }

    private HighRiskDetected createHighRiskDetectedEvent(TransactionId transactionId) {
        return new HighRiskDetected(
                transactionId, AssessmentId.generate(),
                TransactionRiskLevel.HIGH,
                Instant.now()
        );
    }

    private static Map<String, Long> groupByTopic(List<ConsumerRecord<String, Object>> receivedRecords) {
        return receivedRecords.stream()
                .collect(Collectors.groupingBy(ConsumerRecord::topic, Collectors.counting()));
    }

    private List<ConsumerRecord<String, Object>> poolConsumerRecords(int x, int events) {
        List<ConsumerRecord<String, Object>> receivedRecords = new ArrayList<>();
        long endTime = System.currentTimeMillis() + x; // 15 second timeout

        while (System.currentTimeMillis() < endTime && receivedRecords.size() < events) {
            ConsumerRecords<String, Object> polled = testConsumer.poll(Duration.ofSeconds(2));
            polled.forEach(receivedRecords::add);
        }
        return receivedRecords;
    }
}