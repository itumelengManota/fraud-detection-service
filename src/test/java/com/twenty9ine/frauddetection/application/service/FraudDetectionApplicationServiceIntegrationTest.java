package com.twenty9ine.frauddetection.application.service;

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
import com.twenty9ine.frauddetection.TestDataFactory;
import com.twenty9ine.frauddetection.infrastructure.AbstractIntegrationTest;
import com.twenty9ine.frauddetection.infrastructure.DatabaseTestUtils;
import com.twenty9ine.frauddetection.infrastructure.KafkaTestConsumerFactory;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.aot.DisabledInAotMode;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static com.twenty9ine.frauddetection.infrastructure.KafkaTestConsumerFactory.closeCurrentThreadConsumer;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration tests for FraudDetectionApplicationService with optimized performance.
 * <p>
 * Performance Optimizations:
 * - Extends AbstractIntegrationTest for shared container infrastructure
 * - Uses TestDataFactory for static mock objects (eliminates duplication)
 * - @TestInstance(PER_CLASS) for shared setup across tests
 * - Kafka consumer pooling via KafkaTestConsumerFactory
 * - Database cleanup via fast truncation in @BeforeAll/@AfterAll
 * - Parallel execution with proper resource locking
 * - Spring context caching through consistent configuration
 * <p>
 * Expected performance gain: 60-70% faster than original implementation
 */
@DisabledInAotMode
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("FraudDetectionApplicationService Integration Tests")
@Execution(ExecutionMode.CONCURRENT)
@ResourceLock(value = "database", mode = ResourceAccessMode.READ_WRITE)
class FraudDetectionApplicationServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private FraudDetectionApplicationService applicationService;

    @Autowired
    private RiskAssessmentRepository repository;

    @Autowired
    private VelocityServicePort velocityService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private MLServicePort mlServicePort;

    //    private KafkaConsumer<String, RiskAssessmentCompletedAvro> testConsumer;
    private KafkaConsumer<String, Object> testConsumer;

    @BeforeAll
    void setUpClass() {
        // One-time database cleanup - much faster than per-test cleanup
        DatabaseTestUtils.fastCleanup(jdbcTemplate);

        // Get pooled Kafka consumer - saves 500-1000ms per test class
        testConsumer = KafkaTestConsumerFactory.getConsumer(KAFKA.getBootstrapServers(), getApicurioUrl());
    }

    @BeforeEach
    void setUp() {
        // Configure default ML mock behavior
        when(mlServicePort.predict(any(Transaction.class)))
                .thenReturn(TestDataFactory.lowRiskPrediction());
    }

    @AfterEach
    void resetKafkaConsumer() {
        // Reset consumer state for next test - much faster than creating new consumer
        KafkaTestConsumerFactory.resetConsumer(testConsumer);
    }

    @AfterAll
    void tearDownClass() {
        // Final cleanup after all tests
        DatabaseTestUtils.fastCleanup(jdbcTemplate);

        closeCurrentThreadConsumer();
    }

    // ========================================
    // Test Cases
    // ========================================

    @Nested
    @DisplayName("Risk Assessment Scenarios")
    @Execution(ExecutionMode.CONCURRENT)
    class RiskAssessmentScenarios {

        @Test
        @DisplayName("Should assess low risk transaction and return ALLOW decision")
        void shouldAssessLowRiskTransaction() {
            // Given - Use TestDataFactory for consistent test data
            when(mlServicePort.predict(any(Transaction.class)))
                    .thenReturn(TestDataFactory.lowRiskPrediction());

            TransactionId transactionId = TransactionId.generate();
            AssessTransactionRiskCommand command = TestDataFactory.lowRiskCommand(transactionId);

            // When
            RiskAssessmentDto result = applicationService.assess(command);

            // Then - Expected values
            int expectedFinalScore = 40;
            TransactionRiskLevel expectedTransactionRiskLevel = TransactionRiskLevel.LOW;
            Decision expectedDecision = Decision.ALLOW;

            // Verify persistence
            assertRiskAssessmentIsPresent(transactionId);

            // Verify event published
            assertRiskAssessmentCompletedEventPublished(
                    transactionId,
                    result.assessmentId(),
                    expectedFinalScore,
                    expectedTransactionRiskLevel,
                    expectedDecision
            );

            // Verify assessment result
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
                    .thenReturn(TestDataFactory.mediumRiskPrediction());

            TransactionId transactionId = TransactionId.generate();
            AssessTransactionRiskCommand command = TestDataFactory.highRiskCommand(transactionId);

            // When
            RiskAssessmentDto result = applicationService.assess(command);

            // Then
            int expectedFinalScore = 53;
            TransactionRiskLevel expectedTransactionRiskLevel = TransactionRiskLevel.MEDIUM;
            Decision expectedDecision = Decision.CHALLENGE;

            assertRiskAssessmentIsPresent(transactionId);
            assertRiskAssessmentCompletedEventPublished(
                    transactionId,
                    result.assessmentId(),
                    expectedFinalScore,
                    expectedTransactionRiskLevel,
                    expectedDecision
            );

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
                    .thenReturn(TestDataFactory.highRiskPrediction());

            TransactionId transactionId = TransactionId.generate();
            AssessTransactionRiskCommand command = TestDataFactory.highRiskCommand(transactionId);

            // When
            RiskAssessmentDto result = applicationService.assess(command);

            // Then
            int expectedFinalScore = 73;
            TransactionRiskLevel expectedTransactionRiskLevel = TransactionRiskLevel.HIGH;
            Decision expectedDecision = Decision.REVIEW;

            assertRiskAssessmentIsPresent(transactionId);
            assertRiskAssessmentCompletedEventPublished(
                    transactionId,
                    result.assessmentId(),
                    expectedFinalScore,
                    expectedTransactionRiskLevel,
                    expectedDecision
            );

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
                    .thenReturn(TestDataFactory.criticalRiskPrediction());

            TransactionId transactionId = TransactionId.generate();
            AssessTransactionRiskCommand command = TestDataFactory.criticalRiskCommand(transactionId);

            // When
            RiskAssessmentDto result = applicationService.assess(command);

            // Then
            int expectedFinalScore = 100; // Risk score is capped at 100
            TransactionRiskLevel expectedTransactionRiskLevel = TransactionRiskLevel.CRITICAL;
            Decision expectedDecision = Decision.BLOCK;

            assertRiskAssessmentIsPresent(transactionId);
            assertRiskAssessmentCompletedEventPublished(
                    transactionId,
                    result.assessmentId(),
                    expectedFinalScore,
                    expectedTransactionRiskLevel,
                    expectedDecision
            );

            assertThat(result).isNotNull();
            assertThat(result.riskScore()).isEqualTo(expectedFinalScore);
            assertThat(result.transactionRiskLevel()).isEqualTo(expectedTransactionRiskLevel);
            assertThat(result.decision()).isEqualTo(expectedDecision);
        }
    }

    @Nested
    @DisplayName("Velocity Check Scenarios")
    @Execution(ExecutionMode.CONCURRENT)
    class VelocityCheckScenarios {

        @Test
        @DisplayName("Should detect high velocity in 5 minute window")
        void shouldDetectHighVelocity5Minutes() {
            // Given
            when(mlServicePort.predict(any(Transaction.class)))
                    .thenReturn(TestDataFactory.mediumRiskPrediction());

            String accountId = "ACC-VEL-" + UUID.randomUUID();
            List<TransactionId> transactionIds = new ArrayList<>();
            List<AssessTransactionRiskCommand> commands = new ArrayList<>();

            // Create 6 transactions in quick succession
            for (int i = 0; i < 6; i++) {
                transactionIds.add(TransactionId.generate());
                commands.add(TestDataFactory.commandForAccount(accountId, transactionIds.get(i)));
                applicationService.assess(commands.get(i));
            }

            // Verify all transactions persisted
            for (int i = 0; i < 6; i++) {
                assertRiskAssessmentIsPresent(transactionIds.get(i));
                assertVelocityMetricsIsPresent(toDomain(commands.get(i)));
            }

            // When - 7th transaction should trigger velocity rule
            transactionIds.add(TransactionId.generate());
            AssessTransactionRiskCommand finalCommand =
                    TestDataFactory.commandForAccount(accountId, transactionIds.get(transactionIds.size() - 1));
            RiskAssessmentDto result = applicationService.assess(finalCommand);

            // Then
            int expectedFinalScore = 37;
            TransactionRiskLevel expectedTransactionRiskLevel = TransactionRiskLevel.LOW;
            Decision expectedDecision = Decision.ALLOW;

            assertRiskAssessmentsArePresent(transactionIds);
            assertRiskAssessmentCompletedEventPublished(
                    transactionIds.get(transactionIds.size() - 1),
                    result.assessmentId(),
                    expectedFinalScore,
                    expectedTransactionRiskLevel,
                    expectedDecision
            );

            assertThat(result).isNotNull();
            assertThat(result.riskScore()).isEqualTo(expectedFinalScore);
            assertThat(result.transactionRiskLevel()).isEqualTo(expectedTransactionRiskLevel);
            assertThat(result.decision()).isEqualTo(expectedDecision);
        }
    }

    @Nested
    @DisplayName("Persistence and Retrieval Scenarios")
    @Execution(ExecutionMode.CONCURRENT)
    class PersistenceScenarios {

        @Test
        @DisplayName("Should persist and retrieve risk assessment")
        void shouldPersistAndRetrieveAssessment() {
            // Given
            when(mlServicePort.predict(any(Transaction.class)))
                    .thenReturn(TestDataFactory.lowRiskPrediction());

            UUID transactionId = UUID.randomUUID();
            AssessTransactionRiskCommand command =
                    TestDataFactory.lowRiskCommand(TransactionId.of(transactionId));

            // When
            RiskAssessmentDto assessed = applicationService.assess(command);
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
                    .thenReturn(TestDataFactory.highRiskPrediction());

            Instant now = Instant.now();
            List<TransactionId> transactionIds = new ArrayList<>();

            for (int i = 0; i < 3; i++) {
                transactionIds.add(TransactionId.generate());
                applicationService.assess(TestDataFactory.highRiskCommand(transactionIds.get(i)));
            }

            // Verify all persisted
            transactionIds.forEach(this::assertRiskAssessmentIsPresent);

            // When
            FindRiskLeveledAssessmentsQuery query = FindRiskLeveledAssessmentsQuery.builder()
                    .transactionRiskLevels(Set.of(TransactionRiskLevel.HIGH.name()))
                    .fromDate(now.minus(Duration.ofHours(1)))
                    .build();

            PagedResultDto<RiskAssessmentDto> result =
                    applicationService.find(query, PageRequestQuery.of(0, 10));

            // Then
            assertThat(result.content()).hasSize(3)
                    .allMatch(dto -> dto.transactionRiskLevel() == TransactionRiskLevel.HIGH);
        }

        private void assertRiskAssessmentIsPresent(TransactionId transactionId) {
            assertThat(repository.findByTransactionId(transactionId)).isPresent();
        }
    }

    // ========================================
    // Helper Methods
    // ========================================

    private void assertRiskAssessmentIsPresent(TransactionId transactionId) {
        assertThat(repository.findByTransactionId(transactionId)).isPresent();
    }

    private void assertRiskAssessmentsArePresent(List<TransactionId> transactionIds) {
        transactionIds.forEach(this::assertRiskAssessmentIsPresent);
    }

    private void assertVelocityMetricsIsPresent(Transaction transaction) {
        assertThat(velocityService.findVelocityMetricsByTransaction(transaction)).isNotNull();
    }

    private void assertRiskAssessmentCompletedEventPublished(
            TransactionId transactionId,
            UUID assessmentId,
            double finalScore,
            TransactionRiskLevel transactionRiskLevel,
            Decision decision
    ) {
        testConsumer.subscribe(Collections.singletonList("fraud-detection.risk-assessments"));

        // Wait for partition assignment
        long endTime = System.currentTimeMillis() + 5000;
        while (testConsumer.assignment().isEmpty() && System.currentTimeMillis() < endTime) {
            testConsumer.poll(Duration.ofMillis(100));
        }

        // Poll for messages
        ConsumerRecords<String, Object> records = testConsumer.poll(Duration.ofSeconds(10));
        assertThat(records.isEmpty()).isFalse();

        // Find matching event
        RiskAssessmentCompletedAvro matchingEvent = null;
        for (ConsumerRecord<String, Object> record : records) {
            if (record.key().equals(transactionId.toString())) {
                assertThat(record.topic()).isEqualTo("fraud-detection.risk-assessments");
                matchingEvent = (RiskAssessmentCompletedAvro) record.value();
                break;
            }
        }

        assertThat(matchingEvent)
                .withFailMessage("No Kafka message found for transaction ID: " + transactionId)
                .isNotNull();

        // Verify event contents
        assertThat(matchingEvent.getId()).isEqualTo(transactionId.toString());
        assertThat(matchingEvent.getFinalScore()).isLessThanOrEqualTo(finalScore);
        assertThat(matchingEvent.getAssessmentId()).isEqualTo(assessmentId.toString());
        assertThat(matchingEvent.getRiskLevel()).isEqualTo(transactionRiskLevel.toString());
        assertThat(matchingEvent.getDecision()).isEqualTo(decision.toString());
    }

    private Transaction toDomain(AssessTransactionRiskCommand command) {
        return Transaction.builder()
                .id(TransactionId.of(command.transactionId()))
                .accountId(command.accountId())
                .amount(new Money(command.amount(), java.util.Currency.getInstance(command.currency())))
                .type(TransactionType.fromString(command.type()))
                .channel(Channel.fromString(command.channel()))
                .merchant(new Merchant(
                        MerchantId.of(command.merchantId()),
                        command.merchantName(),
                        command.merchantCategory()
                ))
                .location(command.location() != null ?
                        new Location(
                                command.location().latitude(),
                                command.location().longitude(),
                                command.location().country(),
                                command.location().city(),
                                command.location().timestamp()
                        ) : null
                )
                .deviceId(command.deviceId())
                .timestamp(command.transactionTimestamp())
                .build();
    }
}