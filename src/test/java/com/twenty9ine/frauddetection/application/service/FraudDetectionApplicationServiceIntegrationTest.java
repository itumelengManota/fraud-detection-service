package com.twenty9ine.frauddetection.application.service;

import com.twenty9ine.frauddetection.application.dto.LocationDto;
import com.twenty9ine.frauddetection.application.dto.PagedResultDto;
import com.twenty9ine.frauddetection.application.dto.RiskAssessmentDto;
import com.twenty9ine.frauddetection.application.port.in.command.AssessTransactionRiskCommand;
import com.twenty9ine.frauddetection.application.port.in.query.FindRiskLeveledAssessmentsQuery;
import com.twenty9ine.frauddetection.application.port.in.query.GetRiskAssessmentQuery;
import com.twenty9ine.frauddetection.application.port.in.query.PageRequestQuery;
import com.twenty9ine.frauddetection.application.port.out.MLServicePort;
import com.twenty9ine.frauddetection.application.port.out.RiskAssessmentRepository;
import com.twenty9ine.frauddetection.application.port.out.VelocityServicePort;
import com.twenty9ine.frauddetection.domain.valueobject.*;
import com.twenty9ine.frauddetection.infrastructure.adapter.kafka.RiskAssessmentCompletedAvro;
import io.apicurio.registry.serde.SerdeConfig;
import io.apicurio.registry.serde.avro.AvroKafkaDeserializer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisabledInAotMode
@SpringBootTest
@Testcontainers
@DisplayName("FraudDetectionApplicationService Integration Tests")
@Execution(ExecutionMode.SAME_THREAD)
class FraudDetectionApplicationServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"))
            .withDatabaseName("frauddetection_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    @Container
    static org.testcontainers.kafka.KafkaContainer kafka = new org.testcontainers.kafka.KafkaContainer(DockerImageName.parse("apache/kafka:latest"))
            .withReuse(true);

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withReuse(true);

    @Container
    static GenericContainer<?> schemaRegistry = new GenericContainer<>(DockerImageName.parse("apicurio/apicurio-registry-mem:2.6.13.Final"))
            .withExposedPorts(8080)
            .dependsOn(kafka)
            .withReuse(true);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);

        String apicurioUrl = getApicurioUrl();
        registry.add("apicurio.registry.url", () -> apicurioUrl);
        registry.add("spring.kafka.consumer.properties.apicurio.registry.url", () -> apicurioUrl);
        registry.add("spring.kafka.producer.properties.apicurio.registry.url", () -> apicurioUrl);

        registry.add("aws.sagemaker.enabled", () -> "false");
    }

//    private String uniqueAccountId(String base) {
//        return base + "-" + System.currentTimeMillis();
//    }

    private static String getApicurioUrl() {
        return "http://" + schemaRegistry.getHost() + ":" + schemaRegistry.getFirstMappedPort() + "/apis/registry/v2";
    }

    @Autowired
    private FraudDetectionApplicationService applicationService;

    @Autowired
    private RiskAssessmentRepository repository;

    @Autowired
    private VelocityServicePort velocityService;

    @MockitoBean
    private MLServicePort mlServicePort;

    @Autowired
    private KafkaTemplate<String, RiskAssessmentCompletedAvro> kafkaTemplate;

    private KafkaConsumer<String, RiskAssessmentCompletedAvro> createTestConsumer() {
        Map<String, Object> consumerConfig = new HashMap<>();
        consumerConfig.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + UUID.randomUUID());
        consumerConfig.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerConfig.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, AvroKafkaDeserializer.class);
        consumerConfig.put(SerdeConfig.REGISTRY_URL, getApicurioUrl());
        consumerConfig.put("apicurio.registry.use-specific-avro-reader", true);
        consumerConfig.put("specific.avro.reader", "true");
        consumerConfig.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        return new KafkaConsumer<>(consumerConfig);
    }

    @Nested
    @DisplayName("Risk Assessment Scenarios")
    class RiskAssessmentScenarios {

        @Test
        @DisplayName("Should assess low risk transaction and return ALLOW decision")
        void shouldAssessLowRiskTransaction() {
            // Given
            when(mlServicePort.predict(any(Transaction.class)))
                    .thenReturn(mockLowRiskPrediction());

            // When
            TransactionId transactionId = TransactionId.generate();
            AssessTransactionRiskCommand command = FraudDetectionApplicationServiceIntegrationTest.this.buildLowRiskCommand(transactionId);
            RiskAssessmentDto result = applicationService.assess(command);
            
            int expectedFinalScore = 40;
            TransactionRiskLevel expectedTransactionRiskLevel = TransactionRiskLevel.LOW;
            Decision expectedDecision = Decision.ALLOW;

            // Then - Verify persistence
            assertRiskAssessmentIsPresent(transactionId);
            // Then - Verify event published
            assertRiskAssessmentCompletedAvroPublished(transactionId, result.assessmentId(), expectedFinalScore, expectedTransactionRiskLevel, expectedDecision);

            // Then - Verify assessment result
            assertThat(result).isNotNull();
            assertThat(result.riskScore()).isLessThanOrEqualTo(expectedFinalScore);
            assertThat(result.transactionRiskLevel()).isEqualTo(expectedTransactionRiskLevel);
            assertThat(result.decision()).isEqualTo(expectedDecision);
            verify(mlServicePort).predict(any(Transaction.class));
        }

        @Test
        @DisplayName("Should assess medium risk transaction and return CHALLENGE decision")
        void shouldAssessMediumRiskTransaction() {
            // Given
            when(mlServicePort.predict(any(Transaction.class)))
                    .thenReturn(mockMediumRiskPrediction());

            // When
            TransactionId transactionId = TransactionId.generate();
            AssessTransactionRiskCommand command = buildHighRiskCommand(transactionId);
            RiskAssessmentDto result = applicationService.assess(command);

            int expectedFinalScore = 53;
            TransactionRiskLevel expectedTransactionRiskLevel = TransactionRiskLevel.MEDIUM;
            Decision expectedDecision = Decision.CHALLENGE;

            // Then - Verify persistence
            assertRiskAssessmentIsPresent(transactionId);
            // Then - Verify event published
            assertRiskAssessmentCompletedAvroPublished(transactionId, result.assessmentId(), expectedFinalScore, expectedTransactionRiskLevel, expectedDecision);

            // Then - Verify assessment result
            assertThat(result).isNotNull();
            assertThat(result.riskScore()).isEqualTo(expectedFinalScore);
            assertThat(result.transactionRiskLevel()).isEqualTo(expectedTransactionRiskLevel);
            assertThat(result.decision()).isEqualTo(expectedDecision);
        }

        @Test
        @DisplayName("Should assess high risk transaction and return REVIEW decision")
        void shouldAssessHighRiskTransaction() {
            // Given
            when(mlServicePort.predict(any(Transaction.class)))
                    .thenReturn(mockHighRiskPrediction());


            // When
            TransactionId transactionId = TransactionId.generate();

            int expectedFinalScore = 73;
            TransactionRiskLevel expectedTransactionRiskLevel = TransactionRiskLevel.HIGH;
            Decision expectedDecision = Decision.REVIEW;
            AssessTransactionRiskCommand command = buildHighRiskCommand(transactionId);
            RiskAssessmentDto result = applicationService.assess(command);

            // Then - Verify persistence
            assertRiskAssessmentIsPresent(transactionId);
            // Then - Verify event published
            assertRiskAssessmentCompletedAvroPublished(transactionId, result.assessmentId(), expectedFinalScore, expectedTransactionRiskLevel, expectedDecision);

            // Then - Verify assessment result
            assertThat(result).isNotNull();
            assertThat(result.riskScore()).isEqualTo(expectedFinalScore);
            assertThat(result.transactionRiskLevel()).isEqualTo(expectedTransactionRiskLevel);
            assertThat(result.decision()).isEqualTo(expectedDecision);
        }

        @Test
        @DisplayName("Should assess critical risk transaction and return BLOCK decision")
        void shouldAssessCriticalRiskTransaction() {
            // Given
            when(mlServicePort.predict(any(Transaction.class)))
                    .thenReturn(mockCriticalRiskPrediction());

            // When
            TransactionId transactionId = TransactionId.generate();

            int expectedFinalScore = 100;  //Risk score is capped at 100
            TransactionRiskLevel expectedTransactionRiskLevel = TransactionRiskLevel.CRITICAL;
            Decision expectedDecision = Decision.BLOCK;
            AssessTransactionRiskCommand command = buildCriticalRiskCommand(transactionId);
            RiskAssessmentDto result = applicationService.assess(command);

            // Then - Verify persistence
            assertRiskAssessmentIsPresent(transactionId);
            // Then - Verify event published
            assertRiskAssessmentCompletedAvroPublished(transactionId, result.assessmentId(), expectedFinalScore, expectedTransactionRiskLevel, expectedDecision);

            // Then - Verify assessment result
            assertThat(result).isNotNull();
            assertThat(result.riskScore()).isEqualTo(expectedFinalScore);
            assertThat(result.transactionRiskLevel()).isEqualTo(expectedTransactionRiskLevel);
            assertThat(result.decision()).isEqualTo(expectedDecision);
        }
    }

    @Nested
    @DisplayName("Velocity Check Scenarios")
    class VelocityCheckScenarios {

        @Test
        @DisplayName("Should detect high velocity in 5 minute window")
        void shouldDetectHighVelocity5Minutes() {
            // Given
            when(mlServicePort.predict(any(Transaction.class)))
                    .thenReturn(mockMediumRiskPrediction());

            String accountId = "ACC-VEL-001";
            List<TransactionId> transactionIds = new ArrayList<>();
            List<AssessTransactionRiskCommand> commands = new ArrayList<>();

            int expectedFinalScore = 37;
            TransactionRiskLevel expectedTransactionRiskLevel = TransactionRiskLevel.LOW;
            Decision expectedDecision = Decision.ALLOW;

            // Create 5 transactions in quick succession
            for (int i = 0; i < 6; i++) {
                transactionIds.add(TransactionId.generate());
                commands.add(buildCommandForAccount(accountId, transactionIds.get(i)));
                applicationService.assess(commands.get(i));
            }

            for (int i = 0; i < 6; i++) {
                assertRiskAssessmentIsPresent(transactionIds.get(i));
                assertVelocityMetricsIsPresent(FraudDetectionApplicationServiceIntegrationTest.this.toDomain(commands.get(i)));
            }

            // When - 6th transaction should trigger velocity rule
            transactionIds.add(TransactionId.generate());
            AssessTransactionRiskCommand finalCommand = buildCommandForAccount(accountId, transactionIds.getLast());
            RiskAssessmentDto result = applicationService.assess(finalCommand);

            // Then - Verify persistence
            assertRiskAssessmentsArePresent(transactionIds);

            // Then - Verify event published
            assertRiskAssessmentCompletedAvroPublished(transactionIds.getLast(), result.assessmentId(), expectedFinalScore, expectedTransactionRiskLevel, expectedDecision);

            // Then - Verify assessment result
            assertThat(result).isNotNull();
            assertThat(result.riskScore()).isEqualTo(expectedFinalScore);
            assertThat(result.transactionRiskLevel()).isEqualTo(expectedTransactionRiskLevel);
            assertThat(result.decision()).isEqualTo(expectedDecision);
        }
    }

    private void assertVelocityMetricsIsPresent(Transaction transaction) {
        assertThat(velocityService.findVelocityMetricsByTransaction(transaction)).isNotNull();
    }

    @Nested
    @DisplayName("Persistence and Retrieval Scenarios")
    class PersistenceScenarios {

        @Test
        @DisplayName("Should persist and retrieve risk assessment")
        void shouldPersistAndRetrieveAssessment() {
            // Given
            when(mlServicePort.predict(any(Transaction.class)))
                    .thenReturn(mockLowRiskPrediction());

            UUID transactionId = UUID.randomUUID();

            // When
            RiskAssessmentDto assessed = applicationService.assess(buildCommandWithTransactionId(transactionId));
            RiskAssessmentDto retrieved = applicationService.get(new GetRiskAssessmentQuery(transactionId));

            // Then
            assertThat(retrieved).isNotNull();
            assertThat(retrieved.transactionId()).isEqualTo(transactionId);
            assertThat(retrieved.riskScore()).isEqualTo(assessed.riskScore());
        }

        @Test
        @DisplayName("Should find risk assessments by level and time")
        void shouldFindRiskAssessmentsByLevelAndTime() {
            // Given
            when(mlServicePort.predict(any(Transaction.class)))
                    .thenReturn(mockHighRiskPrediction());

            Instant now = Instant.now();
            List<TransactionId> transactionIds = new ArrayList<>();

            for (int i = 0; i < 3; i++) {
                transactionIds.add(TransactionId.generate());
                applicationService.assess(buildHighRiskCommand(transactionIds.get(i)));
            }

            for (int i = 0; i < 3; i++) {
                assertThat(repository.findByTransactionId(transactionIds.get(i))).isPresent();
            }

            // When
            FindRiskLeveledAssessmentsQuery query = FindRiskLeveledAssessmentsQuery.builder()
                    .transactionRiskLevels(Set.of(TransactionRiskLevel.HIGH.name()))
                    .fromDate(now.minus(Duration.ofHours(1)))
                    .build();

            PagedResultDto<RiskAssessmentDto> result = applicationService.find(query, PageRequestQuery.of(0, 10));

            // Then
            assertThat(result.content()).hasSize(3)
                                        .allMatch(dto -> dto.transactionRiskLevel() == TransactionRiskLevel.HIGH);
        }
    }

    private void assertRiskAssessmentIsPresent(TransactionId transactionId) {
        assertThat(repository.findByTransactionId(transactionId)).isPresent();
    }

    private void assertRiskAssessmentsArePresent(List<TransactionId> transactionIds) {
        transactionIds.forEach(this::assertRiskAssessmentIsPresent);
    }


    private void assertRiskAssessmentCompletedAvroPublished(TransactionId transactionId, UUID assessmentId, double finalScore, TransactionRiskLevel transactionRiskLevel, Decision decision) {
        // Create a fresh consumer for this specific assertion
        KafkaConsumer<String, RiskAssessmentCompletedAvro> testConsumer = createTestConsumer();
        testConsumer.subscribe(Collections.singletonList("fraud-detection.risk-assessments"));

        try {
            // Poll multiple times to get all available messages
            ConsumerRecords<String, RiskAssessmentCompletedAvro> records = testConsumer.poll(Duration.ofSeconds(10));
            assertThat(records.isEmpty()).isFalse();

            // Find the record matching our transaction ID
            RiskAssessmentCompletedAvro matchingEvent = null;
            for (ConsumerRecord<String, RiskAssessmentCompletedAvro> consumerRecord : records) {
                if (consumerRecord.key().equals(transactionId.toString())) {
                    assertThat(consumerRecord.topic()).isEqualTo("fraud-detection.risk-assessments");
                    matchingEvent = consumerRecord.value();
                    break;
                }
            }

            assertThat(matchingEvent)
                .withFailMessage("No Kafka message found for transaction ID: " + transactionId)
                .isNotNull();

            // Verify the event contents
            assertThat(matchingEvent.getId()).isEqualTo(transactionId.toString());
            assertThat(matchingEvent.getFinalScore()).isLessThanOrEqualTo(finalScore);
            assertThat(matchingEvent.getAssessmentId()).isEqualTo(assessmentId.toString());
            assertThat(matchingEvent.getRiskLevel()).isEqualTo(transactionRiskLevel.toString());
            assertThat(matchingEvent.getDecision()).isEqualTo(decision.toString());
        } finally {
            testConsumer.close();
        }
    }

    //TODO: Add more tests for when ML service is down
    // Mock prediction helpers
    private MLPrediction mockLowRiskPrediction() {
        return new MLPrediction(
                "test-endpoint",
                "1.0.0",
                0.15,
                0.95,
                Map.of("amount", 0.3, "velocity", 0.2)
        );
    }

    private MLPrediction mockMediumRiskPrediction() {
        return new MLPrediction(
                "test-endpoint",
                "1.0.0",
                0.45,
                0.92,
                Map.of("amount", 0.5, "velocity", 0.4)
        );
    }

    private MLPrediction mockHighRiskPrediction() {
        return new MLPrediction(
                "test-endpoint",
                "1.0.0",
                0.78,
                0.88,
                Map.of("amount", 0.7, "geographic", 0.6)
        );
    }

    private MLPrediction mockCriticalRiskPrediction() {
        return new MLPrediction(
                "test-endpoint",
                "1.0.0",
                0.95,
                0.90,
                Map.of("amount", 0.9, "velocity", 0.8, "geographic", 0.85)
        );
    }

    // Command builders
    private AssessTransactionRiskCommand buildLowRiskCommand(TransactionId transactionId) {
        return AssessTransactionRiskCommand.builder()
                .transactionId(transactionId.toUUID())
                .accountId("ACC-001")
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .type("PURCHASE")
                .channel("MOBILE")
                .merchantId("MER-001")
                .merchantName("Safe Store")
                .merchantCategory("RETAIL")
                .transactionTimestamp(Instant.now())
                .location(new LocationDto(-25.7479, 28.2293, "South Africa", "Pretoria", Instant.now()))
                .build();
    }

    private AssessTransactionRiskCommand buildMediumRiskCommand(UUID transactionId) {
        return AssessTransactionRiskCommand.builder()
                .transactionId(transactionId)
                .accountId("ACC-002")
                .amount(new BigDecimal("10000.01"))
                .currency("USD")
                .type("PURCHASE")
                .channel("ONLINE")
                .merchantId("MER-002")
                .merchantName("Electronics Store")
                .merchantCategory("ELECTRONICS")
                .transactionTimestamp(Instant.now())
                .location(new LocationDto(-25.7479, 28.2293, "South Africa", "Pretoria", Instant.now()))
                .build();
    }

    private AssessTransactionRiskCommand buildHighRiskCommand(TransactionId transactionId) {
        return AssessTransactionRiskCommand.builder()
                .transactionId(transactionId.toUUID())
                .accountId("ACC-003")
                .amount(new BigDecimal("50000.01"))
                .currency("USD")
                .type("TRANSFER")
                .channel("ONLINE")
                .merchantId("MER-003")
                .merchantName("Unknown Vendor")
                .merchantCategory("OTHER")
                .transactionTimestamp(Instant.now())
                .location(new LocationDto(-25.7479, 28.2293, "South Africa", "Pretoria", Instant.now()))
                .build();
    }

    private AssessTransactionRiskCommand buildCriticalRiskCommand(TransactionId transactionId) {
        return AssessTransactionRiskCommand.builder()
                .transactionId(transactionId.toUUID())
                .accountId("ACC-004")
                .amount(new BigDecimal("100000.01"))
                .currency("USD")
                .type("TRANSFER")
                .channel("ONLINE")
                .merchantId("MER-UNKNOWN")
                .merchantName("Suspicious Vendor")
                .merchantCategory("HIGH_RISK")
                .transactionTimestamp(Instant.now())
                .location(new LocationDto(-25.7479, 28.2293, "South Africa", "Pretoria", Instant.now()))
                .build();
    }

    private AssessTransactionRiskCommand buildCommandForAccount(String accountId, TransactionId transactionId) {
        return AssessTransactionRiskCommand.builder()
                .transactionId(transactionId.toUUID())
                .accountId(accountId)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .type("PURCHASE")
                .channel("MOBILE")
                .merchantId("MER-VEL")
                .merchantName("Test Merchant")
                .merchantCategory("RETAIL")
                .transactionTimestamp(Instant.now())
                .location(new LocationDto(-25.7479, 28.2293, "South Africa", "Pretoria", Instant.now()))
                .build();
    }

    private AssessTransactionRiskCommand buildCommandWithTransactionId(UUID transactionId) {
        return AssessTransactionRiskCommand.builder()
                .transactionId(transactionId)
                .accountId("ACC-PERSIST")
                .amount(new BigDecimal("200.00"))
                .currency("USD")
                .type("PURCHASE")
                .channel("MOBILE")
                .merchantId("MER-001")
                .merchantName("Test Store")
                .merchantCategory("RETAIL")
                .transactionTimestamp(Instant.now())
                .location(new LocationDto(-25.7479, 28.2293, "South Africa", "Pretoria", Instant.now()))
                .build();
    }

    /**
     * Converts this command to a Transaction domain object.
     *
     * @return Transaction value object ready for domain processing
     */
    private Transaction toDomain(AssessTransactionRiskCommand command) {
        return Transaction.builder()
                .id(TransactionId.of(command.transactionId()))
                .accountId(command.accountId())
                .amount(new Money(command.amount(), java.util.Currency.getInstance(command.currency())))
                .type(TransactionType.fromString(command.type()))
                .channel(Channel.fromString(command.channel()))
                .merchant(new Merchant(MerchantId.of(command.merchantId()), command.merchantName(), command.merchantCategory()))
                .location(command.location() != null ? toDomain(command.location()) : null)
                .deviceId(command.deviceId())
                .timestamp(command.transactionTimestamp())
                .build();
    }

    private Location toDomain(LocationDto location) {
        return new Location(location.latitude(), location.longitude(), location.country(), location.city(), location.timestamp());
    }
}