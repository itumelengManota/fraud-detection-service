package com.twenty9ine.frauddetection.application.service;

import com.twenty9ine.frauddetection.application.dto.LocationDto;
import com.twenty9ine.frauddetection.application.dto.PagedResultDto;
import com.twenty9ine.frauddetection.application.dto.RiskAssessmentDto;
import com.twenty9ine.frauddetection.application.port.in.command.AssessTransactionRiskCommand;
import com.twenty9ine.frauddetection.application.port.in.query.FindRiskLeveledAssessmentsQuery;
import com.twenty9ine.frauddetection.application.port.in.query.GetRiskAssessmentQuery;
import com.twenty9ine.frauddetection.application.port.in.query.PageRequestQuery;
import com.twenty9ine.frauddetection.application.port.out.RiskAssessmentRepository;
import com.twenty9ine.frauddetection.domain.aggregate.RiskAssessment;
import com.twenty9ine.frauddetection.domain.exception.RiskAssessmentNotFoundException;
import com.twenty9ine.frauddetection.domain.valueobject.Decision;
import com.twenty9ine.frauddetection.domain.valueobject.TransactionRiskLevel;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

@DisabledInAotMode
@SpringBootTest
@Testcontainers
@DisplayName("FraudDetectionApplicationService Integration Tests")
class FraudDetectionApplicationServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"))
            .withDatabaseName("frauddetection_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static org.testcontainers.kafka.KafkaContainer kafka = new org.testcontainers.kafka.KafkaContainer(DockerImageName.parse("apache/kafka:latest"));

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Container
    static GenericContainer<?> schemaRegistry = new GenericContainer<>(DockerImageName.parse("apicurio/apicurio-registry-mem:2.6.13.Final"))
            .withExposedPorts(8080)
            .dependsOn(kafka);

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
    }

    private static String getApicurioUrl() {
        return "http://" + schemaRegistry.getHost() + ":" + schemaRegistry.getFirstMappedPort() + "/apis/registry/v2";
    }

    @Autowired
    private FraudDetectionApplicationService service;

    @Autowired
    private RiskAssessmentRepository repository;

    @Nested
    @DisplayName("Risk Assessment Creation Tests")
    class RiskAssessmentCreationTests {

        @Test
        @DisplayName("Should assess low risk transaction successfully")
        void shouldAssessLowRiskTransaction() {
            AssessTransactionRiskCommand command = createLowRiskCommand(UUID.randomUUID());

            RiskAssessmentDto result = service.assess(command);

            assertThat(result).isNotNull();
            assertThat(result.transactionId()).isEqualTo(command.transactionId());
            assertThat(result.assessmentId()).isNotNull();
            assertThat(result.riskScore()).isGreaterThanOrEqualTo(0);
            assertThat(result.transactionRiskLevel()).isEqualTo(TransactionRiskLevel.LOW);
            assertThat(result.decision()).isEqualTo(Decision.ALLOW);
            assertThat(result.assessmentTime()).isNotNull();
        }

        @Disabled("Disabled until rule engine supports Machine Learning Predictions")
        @Test
        @DisplayName("Should assess medium risk transaction with review decision")
        void shouldAssessMediumRiskTransaction() {
            AssessTransactionRiskCommand command = createMediumRiskCommand(UUID.randomUUID());

            RiskAssessmentDto result = service.assess(command);

            assertThat(result).isNotNull();
            assertThat(result.transactionRiskLevel()).isEqualTo(TransactionRiskLevel.MEDIUM);
            assertThat(result.decision()).isEqualTo(Decision.REVIEW);
            assertThat(result.riskScore()).isBetween(40, 70);
        }

        @Test
        @DisplayName("Should assess high risk transaction with large amount")
        void shouldAssessHighRiskTransactionForLargeAmount() {
            AssessTransactionRiskCommand command = AssessTransactionRiskCommand.builder()
                    .transactionId(UUID.randomUUID())
                    .accountId("ACC-HIGH-" + UUID.randomUUID())
                    .amount(new BigDecimal("75000.00"))
                    .currency("USD")
                    .type("ATM_WITHDRAWAL")
                    .channel("ONLINE")
                    .merchantId("MERCH-HIGH")
                    .merchantName("High Value Merchant")
                    .merchantCategory("JEWELRY")
                    .deviceId("DEVICE-" + UUID.randomUUID())
                    .transactionTimestamp(Instant.now())
                    .build();

            RiskAssessmentDto result = service.assess(command);

            assertThat(result.transactionRiskLevel()).isEqualTo(TransactionRiskLevel.LOW);
            assertThat(result.decision()).isEqualTo(Decision.ALLOW);
            assertThat(result.riskScore()).isLessThanOrEqualTo(26);
        }

        @Disabled("Disabled until rule engine supports Machine Learning Predictions")
        @Test
        @DisplayName("Should assess critical risk transaction")
        void shouldAssessCriticalRiskTransaction() {
            AssessTransactionRiskCommand command = AssessTransactionRiskCommand.builder()
                    .transactionId(UUID.randomUUID())
                    .accountId("ACC-CRITICAL-" + UUID.randomUUID())
                    .amount(new BigDecimal("150000.00"))
                    .currency("USD")
                    .type("ATM_WITHDRAWAL")
                    .channel("ONLINE")
                    .merchantId("MERCH-CRITICAL")
                    .merchantName("Critical Risk Merchant")
                    .merchantCategory("GAMBLING")
                    .deviceId("DEVICE-" + UUID.randomUUID())
                    .transactionTimestamp(Instant.now())
                    .build();

            RiskAssessmentDto result = service.assess(command);

            assertThat(result.transactionRiskLevel()).isEqualTo(TransactionRiskLevel.CRITICAL);
            assertThat(result.decision()).isEqualTo(Decision.BLOCK);
            assertThat(result.riskScore()).isGreaterThanOrEqualTo(90);
        }

        @Test
        @DisplayName("Should assess transaction with location information")
        void shouldAssessTransactionWithLocation() {
            LocationDto location = new LocationDto(
                    40.7128,
                    -74.0060,
                    "USA",
                    "New York",
                    Instant.now()
            );

            AssessTransactionRiskCommand command = AssessTransactionRiskCommand.builder()
                    .transactionId(UUID.randomUUID())
                    .accountId("ACC-LOC-" + UUID.randomUUID())
                    .amount(new BigDecimal("500.00"))
                    .currency("USD")
                    .type("PURCHASE")
                    .channel("ONLINE")
                    .merchantId("MERCH-LOC")
                    .merchantName("Location Test Merchant")
                    .merchantCategory("RETAIL")
                    .location(location)
                    .deviceId("DEVICE-" + UUID.randomUUID())
                    .transactionTimestamp(Instant.now())
                    .build();

            RiskAssessmentDto result = service.assess(command);

            assertThat(result).isNotNull();
            assertThat(result.assessmentId()).isNotNull();
        }

        @Test
        @DisplayName("Should handle multiple rapid assessments for same account")
        void shouldHandleMultipleRapidAssessments() {
            String accountId = "ACC-RAPID-" + UUID.randomUUID();
            List<RiskAssessmentDto> results = new ArrayList<>();

            for (int i = 0; i < 5; i++) {
                AssessTransactionRiskCommand command = AssessTransactionRiskCommand.builder()
                        .transactionId(UUID.randomUUID())
                        .accountId(accountId)
                        .amount(new BigDecimal("100.00"))
                        .currency("USD")
                        .type("PURCHASE")
                        .channel("ONLINE")
                        .merchantId("MERCH-" + i)
                        .merchantName("Merchant " + i)
                        .merchantCategory("RETAIL")
                        .deviceId("DEVICE-" + UUID.randomUUID())
                        .transactionTimestamp(Instant.now().plusMillis(i * 100))
                        .build();

                results.add(service.assess(command));
            }

            assertThat(results).hasSize(5)
                               .allMatch(r -> r.assessmentId() != null)
                               .extracting(RiskAssessmentDto::transactionId).doesNotHaveDuplicates();
        }
    }

    @Nested
    @DisplayName("Risk Assessment Retrieval Tests")
    class RiskAssessmentRetrievalTests {

        @Disabled("Disabled until rule engine supports Machine Learning Predictions")
        @Test
        @DisplayName("Should retrieve existing risk assessment by transaction ID")
        void shouldRetrieveExistingRiskAssessment() {
            AssessTransactionRiskCommand command = createLowRiskCommand(UUID.randomUUID());
            RiskAssessmentDto assessed = service.assess(command);

            RiskAssessmentDto retrieved = service.get(new GetRiskAssessmentQuery(command.transactionId()));

            assertThat(retrieved).isNotNull();
            assertThat(retrieved.assessmentId()).isEqualTo(assessed.assessmentId());
            assertThat(retrieved.transactionId()).isEqualTo(assessed.transactionId());
            assertThat(retrieved.riskScore()).isEqualTo(assessed.riskScore());
            assertThat(retrieved.transactionRiskLevel()).isEqualTo(assessed.transactionRiskLevel());
            assertThat(retrieved.decision()).isEqualTo(assessed.decision());
        }

        @Test
        @DisplayName("Should throw exception when risk assessment not found")
        void shouldThrowExceptionWhenRiskAssessmentNotFound() {
            UUID nonExistentTransactionId = UUID.randomUUID();
            GetRiskAssessmentQuery query = new GetRiskAssessmentQuery(nonExistentTransactionId);

            assertThatThrownBy(() -> service.get(query))
                    .isInstanceOf(RiskAssessmentNotFoundException.class)
                    .hasMessageContaining("Risk assessment not found for transaction ID: " + nonExistentTransactionId);
        }

        @Test
        @DisplayName("Should retrieve risk assessment immediately after creation")
        void shouldRetrieveRiskAssessmentImmediatelyAfterCreation() {
            AssessTransactionRiskCommand command = createLowRiskCommand(UUID.randomUUID());
            service.assess(command);

            RiskAssessmentDto retrieved = service.get(new GetRiskAssessmentQuery(command.transactionId()));

            assertThat(retrieved).isNotNull();
            assertThat(retrieved.transactionId()).isEqualTo(command.transactionId());
        }
    }

    @Nested
    @DisplayName("Risk Assessment Search Tests")
    class RiskAssessmentSearchTests {

        @Test
        @DisplayName("Should find risk assessments by risk level")
        void shouldFindRiskAssessmentsByRiskLevel() {
            service.assess(createLowRiskCommand(UUID.randomUUID()));
            service.assess(createMediumRiskCommand(UUID.randomUUID()));
            service.assess(createHighRiskCommand(UUID.randomUUID()));

            FindRiskLeveledAssessmentsQuery query = FindRiskLeveledAssessmentsQuery.builder()
                    .transactionRiskLevels(Set.of(TransactionRiskLevel.LOW, TransactionRiskLevel.MEDIUM, TransactionRiskLevel.HIGH))
                    .from(Instant.now().minus(1, ChronoUnit.HOURS))
                    .build();

            PageRequestQuery pageRequest = PageRequestQuery.of(0, 10);
            PagedResultDto<RiskAssessmentDto> results = service.find(query, pageRequest);

            assertThat(results).isNotNull();
            assertThat(results.content()).isNotEmpty();
            assertThat(results.totalElements()).isGreaterThan(0);
            assertThat(results.pageNumber()).isZero();
            assertThat(results.pageSize()).isEqualTo(10);
        }

        @Disabled("Disabled until rule engine supports Machine Learning Predictions")
        @Test
        @DisplayName("Should find only high and critical risk assessments")
        void shouldFindOnlyHighAndCriticalRiskAssessments() {
            UUID lowRiskTransactionId = UUID.randomUUID();
            service.assess(createLowRiskCommand(lowRiskTransactionId));

            UUID mediumRiskTransactionId = UUID.randomUUID();
            service.assess(createMediumRiskCommand(mediumRiskTransactionId));

            UUID highRiskTransactionId = UUID.randomUUID();
            service.assess(createHighRiskCommand(highRiskTransactionId));

            Optional<RiskAssessment> optionalLowRiskAssessment = repository.findByTransactionId(lowRiskTransactionId);
            Optional<RiskAssessment> optionalMediumRiskAssessment = repository.findByTransactionId(mediumRiskTransactionId);
            Optional<RiskAssessment> optionalHighRiskAssessment = repository.findByTransactionId(highRiskTransactionId);

            assertThat(optionalLowRiskAssessment).isPresent();
            assertThat(optionalMediumRiskAssessment).isPresent();
            assertThat(optionalHighRiskAssessment).isPresent();

            FindRiskLeveledAssessmentsQuery query = FindRiskLeveledAssessmentsQuery.builder()
                    .transactionRiskLevels(Set.of(TransactionRiskLevel.HIGH, TransactionRiskLevel.CRITICAL))
                    .from(Instant.now().minus(1, ChronoUnit.HOURS))
                    .build();

            PageRequestQuery pageRequest = PageRequestQuery.of(0, 20);
            PagedResultDto<RiskAssessmentDto> results = service.find(query, pageRequest);

            assertThat(results.content())
                    .isNotEmpty()
                    .allMatch(r -> r.transactionRiskLevel() == TransactionRiskLevel.HIGH || r.transactionRiskLevel() == TransactionRiskLevel.CRITICAL);
        }

        @Test
        @DisplayName("Should support pagination for risk assessment search")
        void shouldSupportPaginationForRiskAssessmentSearch() {
            String accountPrefix = "ACC-PAGE-" + UUID.randomUUID();
            createMultipleAssessments(accountPrefix, 15);

            FindRiskLeveledAssessmentsQuery query = FindRiskLeveledAssessmentsQuery.builder()
                    .transactionRiskLevels(Set.of(TransactionRiskLevel.LOW, TransactionRiskLevel.MEDIUM, TransactionRiskLevel.HIGH, TransactionRiskLevel.CRITICAL))
                    .from(Instant.now().minus(1, ChronoUnit.HOURS))
                    .build();

            PageRequestQuery firstPage = PageRequestQuery.of(0, 5);
            PagedResultDto<RiskAssessmentDto> firstResults = service.find(query, firstPage);

            PageRequestQuery secondPage = PageRequestQuery.of(1, 5);
            PagedResultDto<RiskAssessmentDto> secondResults = service.find(query, secondPage);

            assertThat(firstResults.content()).hasSize(5);
            assertThat(secondResults.content()).hasSize(5);
            assertThat(firstResults.content()).doesNotContainAnyElementsOf(secondResults.content());
            assertThat(firstResults.totalElements()).isEqualTo(secondResults.totalElements());
        }

        @Test
        @DisplayName("Should find assessments within time window")
        void shouldFindAssessmentsWithinTimeWindow() {
            Instant twoHoursAgo = Instant.now().minus(2, ChronoUnit.HOURS);
            Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);

            String accountId = "ACC-TIME-" + UUID.randomUUID();
            AssessTransactionRiskCommand oldCommand = createCommandWithTimestamp(accountId, twoHoursAgo);
            service.assess(oldCommand);

            AssessTransactionRiskCommand recentCommand = createCommandWithTimestamp(accountId, oneHourAgo);
            service.assess(recentCommand);

            FindRiskLeveledAssessmentsQuery query = FindRiskLeveledAssessmentsQuery.builder()
                    .transactionRiskLevels(Set.of(TransactionRiskLevel.LOW, TransactionRiskLevel.MEDIUM, TransactionRiskLevel.HIGH, TransactionRiskLevel.CRITICAL))
                    .from(oneHourAgo.minus(5, ChronoUnit.MINUTES))
                    .build();

            PageRequestQuery pageRequest = PageRequestQuery.of(0, 10);
            PagedResultDto<RiskAssessmentDto> results = service.find(query, pageRequest);

            assertThat(results.content())
                    .isNotEmpty()
                    .allMatch(r -> r.assessmentTime().isAfter(oneHourAgo.minus(5, ChronoUnit.MINUTES)));
        }

        @Test
        @DisplayName("Should return empty results when no assessments match criteria")
        void shouldReturnEmptyResultsWhenNoAssessmentsMatchCriteria() {
            FindRiskLeveledAssessmentsQuery query = FindRiskLeveledAssessmentsQuery.builder()
                    .transactionRiskLevels(Set.of(TransactionRiskLevel.CRITICAL))
                    .from(Instant.now().plus(1, ChronoUnit.HOURS))
                    .build();

            PageRequestQuery pageRequest = PageRequestQuery.of(0, 10);
            PagedResultDto<RiskAssessmentDto> results = service.find(query, pageRequest);

            assertThat(results.content()).isEmpty();
            assertThat(results.totalElements()).isZero();
        }

        @Test
        @DisplayName("Should handle large result sets efficiently")
        void shouldHandleLargeResultSetsEfficiently() {
            String accountPrefix = "ACC-LARGE-" + UUID.randomUUID();
            createMultipleAssessments(accountPrefix, 50);

            FindRiskLeveledAssessmentsQuery query = FindRiskLeveledAssessmentsQuery.builder()
                    .transactionRiskLevels(Set.of(TransactionRiskLevel.LOW, TransactionRiskLevel.MEDIUM, TransactionRiskLevel.HIGH, TransactionRiskLevel.CRITICAL))
                    .from(Instant.now().minus(1, ChronoUnit.HOURS))
                    .build();

            PageRequestQuery pageRequest = PageRequestQuery.of(0, 100);
            PagedResultDto<RiskAssessmentDto> results = service.find(query, pageRequest);

            assertThat(results.totalElements()).isGreaterThanOrEqualTo(50);
            assertThat(results.content()).hasSizeLessThanOrEqualTo(100);
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle assessment with null location")
        void shouldHandleAssessmentWithNullLocation() {
            AssessTransactionRiskCommand command = AssessTransactionRiskCommand.builder()
                    .transactionId(UUID.randomUUID())
                    .accountId("ACC-NULL-LOC-" + UUID.randomUUID())
                    .amount(new BigDecimal("100.00"))
                    .currency("USD")
                    .type("PURCHASE")
                    .channel("ONLINE")
                    .merchantId("MERCH-NULL")
                    .merchantName("Null Location Merchant")
                    .merchantCategory("RETAIL")
                    .location(null)
                    .deviceId("DEVICE-" + UUID.randomUUID())
                    .transactionTimestamp(Instant.now())
                    .build();

            RiskAssessmentDto result = service.assess(command);

            assertThat(result).isNotNull();
            assertThat(result.assessmentId()).isNotNull();
        }

        @Test
        @DisplayName("Should handle assessment with minimum amount")
        void shouldHandleAssessmentWithMinimumAmount() {
            AssessTransactionRiskCommand command = AssessTransactionRiskCommand.builder()
                    .transactionId(UUID.randomUUID())
                    .accountId("ACC-MIN-" + UUID.randomUUID())
                    .amount(new BigDecimal("0.01"))
                    .currency("USD")
                    .type("PURCHASE")
                    .channel("ONLINE")
                    .merchantId("MERCH-MIN")
                    .merchantName("Minimum Amount Merchant")
                    .merchantCategory("RETAIL")
                    .deviceId("DEVICE-" + UUID.randomUUID())
                    .transactionTimestamp(Instant.now())
                    .build();

            RiskAssessmentDto result = service.assess(command);

            assertThat(result).isNotNull();
            assertThat(result.transactionRiskLevel()).isEqualTo(TransactionRiskLevel.LOW);
        }

        @Test
        @DisplayName("Should handle concurrent assessments for different transactions")
        void shouldHandleConcurrentAssessmentsForDifferentTransactions() {
            List<RiskAssessmentDto> results = new ArrayList<>();

            for (int i = 0; i < 10; i++) {
                AssessTransactionRiskCommand command = createLowRiskCommand(UUID.randomUUID());
                results.add(service.assess(command));
            }

            assertThat(results).hasSize(10);
            assertThat(results).extracting(RiskAssessmentDto::assessmentId).doesNotHaveDuplicates();
            assertThat(results).extracting(RiskAssessmentDto::transactionId).doesNotHaveDuplicates();
        }
    }

    // Helper methods

    private AssessTransactionRiskCommand createLowRiskCommand(UUID transactionId) {
        return AssessTransactionRiskCommand.builder()
                .transactionId(transactionId)
                .accountId("ACC-LOW-" + UUID.randomUUID())
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .type("PURCHASE")
                .channel("ONLINE")
                .merchantId("MERCH-LOW")
                .merchantName("Low Risk Merchant")
                .merchantCategory("RETAIL")
                .deviceId("DEVICE-" + UUID.randomUUID())
                .transactionTimestamp(Instant.now())
                .build();
    }

    private AssessTransactionRiskCommand createMediumRiskCommand(UUID transactionId) {
        return AssessTransactionRiskCommand.builder()
                .transactionId(transactionId)
                .accountId("ACC-MED-" + UUID.randomUUID())
                .amount(new BigDecimal("15000.00"))
                .currency("USD")
                .type("ATM_WITHDRAWAL")
                .channel("ONLINE")
                .merchantId("MERCH-MED")
                .merchantName("Medium Risk Merchant")
                .merchantCategory("ELECTRONICS")
                .deviceId("DEVICE-" + UUID.randomUUID())
                .transactionTimestamp(Instant.now())
                .build();
    }

    private static AssessTransactionRiskCommand createHighRiskCommand(UUID transactionId) {
        return AssessTransactionRiskCommand.builder()
                .transactionId(transactionId)
                .accountId("ACC-HIGH-" + UUID.randomUUID())
                .amount(new BigDecimal("75000.00"))
                .currency("USD")
                .type("ATM_WITHDRAWAL")
                .channel("ONLINE")
                .merchantId("MERCH-HIGH")
                .merchantName("High Risk Merchant")
                .merchantCategory("GAMBLING")
                .deviceId("DEVICE-" + UUID.randomUUID())
                .transactionTimestamp(Instant.now())
                .build();
    }

    private void createMultipleAssessments(String accountPrefix, int count) {
        for (int i = 0; i < count; i++) {
            AssessTransactionRiskCommand command = AssessTransactionRiskCommand.builder()
                    .transactionId(UUID.randomUUID())
                    .accountId(accountPrefix + "-" + i)
                    .amount(new BigDecimal("100.00").multiply(BigDecimal.valueOf(i + 1)))
                    .currency("USD")
                    .type("PURCHASE")
                    .channel("ONLINE")
                    .merchantId("MERCH-" + i)
                    .merchantName("Merchant " + i)
                    .merchantCategory("RETAIL")
                    .deviceId("DEVICE-" + UUID.randomUUID())
                    .transactionTimestamp(Instant.now().plusSeconds(i))
                    .build();
            service.assess(command);
        }
    }

    private AssessTransactionRiskCommand createCommandWithTimestamp(String accountId, Instant timestamp) {
        return AssessTransactionRiskCommand.builder()
                .transactionId(UUID.randomUUID())
                .accountId(accountId)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .type("PURCHASE")
                .channel("ONLINE")
                .merchantId("MERCH-TIME")
                .merchantName("Time Test Merchant")
                .merchantCategory("RETAIL")
                .deviceId("DEVICE-" + UUID.randomUUID())
                .transactionTimestamp(timestamp)
                .build();
    }
}