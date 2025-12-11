package com.twenty9ine.frauddetection.domain.service;

import com.twenty9ine.frauddetection.application.port.out.MLServicePort;
import com.twenty9ine.frauddetection.application.port.out.TransactionRepository;
import com.twenty9ine.frauddetection.application.port.out.VelocityServicePort;
import com.twenty9ine.frauddetection.domain.aggregate.RiskAssessment;
import com.twenty9ine.frauddetection.domain.valueobject.*;
import com.twenty9ine.frauddetection.infrastructure.adapter.cache.VelocityCounterAdapter;
import org.junit.jupiter.api.*;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@DisabledInAotMode
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RiskScoringServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("aws.sagemaker.enabled", () -> "false");
    }

    @TestConfiguration
    @EnableJdbcRepositories(basePackages = "com.twenty9ine.frauddetection.infrastructure.adapter.persistence")
    @ComponentScan(basePackages = {
            "com.twenty9ine.frauddetection.domain.service",
            "com.twenty9ine.frauddetection.application.service",
            "com.twenty9ine.frauddetection.infrastructure.adapter.persistence"
    })
    static class TestConfig {


        @Bean
        public RedissonClient redissonClient() {
            Config config = new Config();
            config.useSingleServer().setAddress(String.format("redis://%s:%d", redis.getHost(), redis.getFirstMappedPort()));

            return Redisson.create(config);
        }

        @Bean
        public CacheManager cacheManager() {
            return new CaffeineCacheManager("velocityMetrics");
        }

        @Primary
        @Bean
        public VelocityServicePort velocityServicePort(RedissonClient redissonClient, CacheManager cacheManager) {
            return new VelocityCounterAdapter(redissonClient, cacheManager);
        }

//        @Primary
//        @Bean
//        public MLServicePort mlServicePort() {
//            return transaction -> new MLPrediction("test-model", "v1.0.0", 0.5,
//                    0.9, Map.of("amount", 0.3, "velocity", 0.4, "location", 0.3));
//        }
    }

    @Autowired
    private RiskScoringService riskScoringService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private VelocityServicePort velocityService;

    @Autowired
    private RedissonClient redissonClient;

    @MockitoBean
    private MLServicePort mlServicePort;

    @BeforeEach
    void setUp() {
        // Clean up Redis before each test
        redissonClient.getKeys().flushall();
    }

    @Test
    @Order(1)
    @DisplayName("Should assess risk for a normal low-risk transaction")
    void shouldAssessRiskForNormalLowRiskTransaction() {
        // Given
        when(mlServicePort.predict(any(Transaction.class)))
                .thenReturn(mockLowRiskPrediction());

        Transaction transaction = createTransaction("ACC-001", BigDecimal.valueOf(50.00));

        // When
        RiskAssessment assessment = riskScoringService.assessRisk(transaction);

        // Then
        assertThat(assessment).isNotNull();
        assertThat(assessment.getTransactionId()).isEqualTo(transaction.id());
        assertThat(assessment.getMlPrediction()).isNotNull();
        assertThat(assessment.getMlPrediction().modelId()).isEqualTo("test-model");
        assertThat(assessment.getRuleEvaluations()).isEmpty(); // No rules triggered for normal transaction
    }

    @Test
    @Order(2)
    @DisplayName("Should assess risk for large amount transaction")
    void shouldAssessRiskForLargeAmountTransaction() {
        // Given
        when(mlServicePort.predict(any(Transaction.class)))
                .thenReturn(mockLowRiskPrediction());

        Transaction transaction = createTransaction("ACC-002", BigDecimal.valueOf(15000.00));

        // When
        RiskAssessment assessment = riskScoringService.assessRisk(transaction);

        // Then
        assertThat(assessment).isNotNull();
        assertThat(assessment.getMlPrediction()).isNotNull();
        assertThat(assessment.getRuleEvaluations()).hasSize(1);
        assertThat(assessment.getRuleEvaluations().getFirst().ruleName()).contains("Large Amount");
    }

    @Test
    @Order(3)
    @DisplayName("Should assess risk for very large amount transaction")
    void shouldAssessRiskForVeryLargeAmountTransaction() {
        // Given
        when(mlServicePort.predict(any(Transaction.class)))
                .thenReturn(mockLowRiskPrediction());

        Transaction transaction = createTransaction("ACC-003", BigDecimal.valueOf(60000.00));

        // When
        RiskAssessment assessment = riskScoringService.assessRisk(transaction);

        // Then
        assertThat(assessment).isNotNull();
        assertThat(assessment.getRuleEvaluations()).hasSize(2);
        assertThat(assessment.getRuleEvaluations())
                .extracting(RuleEvaluation::ruleName)
                .contains("Large Amount", "Very Large Amount");
    }

    @Test
    @Order(4)
    @DisplayName("Should assess risk for high velocity transaction")
    void shouldAssessRiskForHighVelocityTransaction() {
        // Given
        when(mlServicePort.predict(any(Transaction.class)))
                .thenReturn(mockLowRiskPrediction());

        String accountId = "ACC-004";
        Transaction transaction = createTransaction(accountId, BigDecimal.valueOf(100.00));

        // Simulate high velocity by incrementing counters multiple times
        for (int i = 0; i < 8; i++) {
            Transaction tempTransaction = createTransaction(accountId, BigDecimal.valueOf(100.00));
            velocityService.incrementCounters(tempTransaction);
        }

        // When
        RiskAssessment assessment = riskScoringService.assessRisk(transaction);

        // Then
        assertThat(assessment).isNotNull();
        assertThat(assessment.getRuleEvaluations()).isNotEmpty();
        assertThat(assessment.getRuleEvaluations())
                .extracting(RuleEvaluation::ruleName)
                .contains("Medium Velocity 5min");
    }

    @Test
    @Order(5)
    @DisplayName("Should assess risk for extreme velocity transaction")
    void shouldAssessRiskForExtremeVelocityTransaction() {
        // Given
        when(mlServicePort.predict(any(Transaction.class)))
                .thenReturn(mockLowRiskPrediction());

        String accountId = "ACC-005";
        Transaction transaction = createTransaction(accountId, BigDecimal.valueOf(100.00));

        // Simulate extreme velocity
        for (int i = 0; i < 25; i++) {
            Transaction tempTransaction = createTransaction(accountId, BigDecimal.valueOf(100.00));
            velocityService.incrementCounters(tempTransaction);
        }

        // When
        RiskAssessment assessment = riskScoringService.assessRisk(transaction);

        // Then
        assertThat(assessment).isNotNull();
        assertThat(assessment.getRuleEvaluations()).hasSize(2);
        assertThat(assessment.getRuleEvaluations())
                .extracting(RuleEvaluation::ruleName)
                .containsExactlyInAnyOrder("Medium Velocity 5min", "High Velocity 1hr");
    }

    @Test
    @Order(6)
    @DisplayName("Should assess risk for impossible travel scenario")
    void shouldAssessRiskForImpossibleTravelScenario() {
        // Given
        when(mlServicePort.predict(any(Transaction.class)))
                .thenReturn(mockLowRiskPrediction());

        String accountId = "ACC-006";
        Instant now = Instant.now();

        // First transaction in New York
        Location location1 = new Location(40.7128, -74.0060, "USA", "New York", now.minusSeconds(10));
        Transaction transaction1 = createTransactionWithLocation(accountId, location1);
        transactionRepository.save(transaction1);

        // Second transaction in London 10 seconds later (impossible travel)
        Location location2 = new Location(51.5074, -0.1278, "UK", "London", now);
        Transaction transaction2 = createTransactionWithLocation(accountId, location2);

        // When
        RiskAssessment assessment = riskScoringService.assessRisk(transaction2);

        // Then
        assertThat(assessment).isNotNull();
        assertThat(assessment.getRuleEvaluations()).isNotEmpty();
        assertThat(assessment.getRuleEvaluations())
                .extracting(RuleEvaluation::ruleName)
                .contains("Impossible Travel");
    }

    @Test
    @Order(7)
    @DisplayName("Should assess risk with multiple risk factors")
    void shouldAssessRiskWithMultipleRiskFactors() {
        // Given
        when(mlServicePort.predict(any(Transaction.class)))
                .thenReturn(mockLowRiskPrediction());

        String accountId = "ACC-007";
        Instant now = Instant.now();

        // Setup impossible travel
        Location location1 = new Location(40.7128, -74.0060, "USA", "New York", now.minusSeconds(5));
        Transaction previousTransaction = createTransactionWithLocation(accountId, location1);
        transactionRepository.save(previousTransaction);

        // Setup high velocity
        for (int i = 0; i < 10; i++) {
            Transaction tempTransaction = createTransaction(accountId, BigDecimal.valueOf(100.00));
            velocityService.incrementCounters(tempTransaction);
        }

        // Create transaction with large amount and impossible travel
        Location location2 = new Location(51.5074, -0.1278, "UK", "London", now);
        Transaction transaction = createTransactionWithLocationAndAmount(accountId, location2, BigDecimal.valueOf(70000.00));

        // When
        RiskAssessment assessment = riskScoringService.assessRisk(transaction);

        // Then
        assertThat(assessment).isNotNull();
        assertThat(assessment.getMlPrediction()).isNotNull();
        assertThat(assessment.getRuleEvaluations())
                .hasSizeGreaterThanOrEqualTo(3)
                .extracting(RuleEvaluation::ruleName)
                .contains("Large Amount", "Very Large Amount", "Medium Velocity 5min", "Impossible Travel");
    }

    @Test
    @Order(8)
    @DisplayName("Should calculate composite score correctly with ML prediction and rules")
    void shouldCalculateCompositeScoreCorrectly() {
        // Given
        when(mlServicePort.predict(any(Transaction.class)))
                .thenReturn(mockLowRiskPrediction());

        Transaction transaction = createTransaction("ACC-008", BigDecimal.valueOf(55000.00));

        // When
        RiskAssessment assessment = riskScoringService.assessRisk(transaction);

        // Then
        assertThat(assessment).isNotNull();
        assertThat(assessment.getMlPrediction()).isNotNull();

        assertThat(assessment.getRuleEvaluations()).hasSize(2);
        assertThat(assessment.getRuleEvaluations())
                .extracting(RuleEvaluation::ruleName)
                .contains("Large Amount", "Very Large Amount");

        // Verify composite score calculation:
        // ML: 0.15 * 100 * 0.6 = 9
        // Rules: (MEDIUM(25) + HIGH(40)) * 0.4 = 26
        // Total: 9 + 26 = 35
        assertThat(assessment.getRiskScore().value())
                .isEqualTo(35);

        assertThat(assessment.getTransactionRiskLevel()).isEqualTo(TransactionRiskLevel.LOW);
    }


    @Test
    @Order(9)
    @DisplayName("Should handle transaction with no previous history")
    void shouldHandleTransactionWithNoPreviousHistory() {
        // Given
        when(mlServicePort.predict(any(Transaction.class)))
                .thenReturn(mockLowRiskPrediction());

        Transaction transaction = createTransaction("ACC-NEW-001", BigDecimal.valueOf(100.00));

        // When
        RiskAssessment assessment = riskScoringService.assessRisk(transaction);

        // Then
        assertThat(assessment).isNotNull();
        assertThat(assessment.getMlPrediction()).isNotNull();
        assertThat(assessment.getTransactionId()).isEqualTo(transaction.id());
    }

    @Test
    @Order(10)
    @DisplayName("Should assess risk for zero amount transaction")
    void shouldAssessRiskForZeroAmountTransaction() {
        // Given
        when(mlServicePort.predict(any(Transaction.class)))
                .thenReturn(mockLowRiskPrediction());

        Transaction transaction = createTransaction("ACC-010", BigDecimal.ZERO);

        // When
        RiskAssessment assessment = riskScoringService.assessRisk(transaction);

        // Then
        assertThat(assessment).isNotNull();
        assertThat(assessment.getMlPrediction()).isNotNull();
        assertThat(assessment.getRuleEvaluations()).isEmpty(); // No amount rules triggered
    }

    @Test
    @Order(11)
    @DisplayName("Should assess risk for boundary amount at 10000")
    void shouldAssessRiskForBoundaryAmountAt10000() {
        // Given
        when(mlServicePort.predict(any(Transaction.class)))
                .thenReturn(mockLowRiskPrediction());

        Transaction transaction = createTransaction("ACC-011", BigDecimal.valueOf(10001.00));

        // When
        RiskAssessment assessment = riskScoringService.assessRisk(transaction);

        // Then
        assertThat(assessment).isNotNull();
        assertThat(assessment.getRuleEvaluations()).hasSize(1);
        assertThat(assessment.getRuleEvaluations().get(0).ruleName()).contains("Large Amount");
    }

    @Test
    @Order(12)
    @DisplayName("Should assess risk for boundary amount at 50000")
    void shouldAssessRiskForBoundaryAmountAt50000() {
        // Given
        when(mlServicePort.predict(any(Transaction.class)))
                .thenReturn(mockLowRiskPrediction());

        Transaction transaction = createTransaction("ACC-012", BigDecimal.valueOf(50001.00));

        // When
        RiskAssessment assessment = riskScoringService.assessRisk(transaction);

        // Then
        assertThat(assessment).isNotNull();
        assertThat(assessment.getRuleEvaluations()).hasSize(2);
    }

    @Test
    @Order(13)
    @DisplayName("Should assess risk with exactly 6 transactions in 5 minutes")
    void shouldAssessRiskWithExactly6TransactionsIn5Minutes() {
        // Given
        when(mlServicePort.predict(any(Transaction.class)))
                .thenReturn(mockLowRiskPrediction());

        String accountId = "ACC-013";
        Transaction transaction = createTransaction(accountId, BigDecimal.valueOf(100.00));

        // Simulate exactly 6 transactions
        for (int i = 0; i < 6; i++) {
            Transaction tempTransaction = createTransaction(accountId, BigDecimal.valueOf(100.00));
            velocityService.incrementCounters(tempTransaction);
        }

        // When
        RiskAssessment assessment = riskScoringService.assessRisk(transaction);

        // Then
        assertThat(assessment).isNotNull();
        assertThat(assessment.getRuleEvaluations()).isNotEmpty();
    }

    @Test
    @Order(14)
    @DisplayName("Should assess risk with exactly 21 transactions in 1 hour")
    void shouldAssessRiskWithExactly21TransactionsIn1Hour() {
        // Given
        when(mlServicePort.predict(any(Transaction.class)))
                .thenReturn(mockLowRiskPrediction());

        String accountId = "ACC-014";
        Transaction transaction = createTransaction(accountId, BigDecimal.valueOf(100.00));

        // Simulate exactly 21 transactions
        for (int i = 0; i < 21; i++) {
            Transaction tempTransaction = createTransaction(accountId, BigDecimal.valueOf(100.00));
            velocityService.incrementCounters(tempTransaction);
        }

        // When
        RiskAssessment assessment = riskScoringService.assessRisk(transaction);

        // Then
        assertThat(assessment).isNotNull();
        assertThat(assessment.getRuleEvaluations()).hasSize(2); // Both velocity rules triggered
    }

    @Test
    @Order(15)
    @DisplayName("Should handle concurrent risk assessments for same account")
    void shouldHandleConcurrentRiskAssessmentsForSameAccount() throws InterruptedException {
        // Given
        when(mlServicePort.predict(any(Transaction.class)))
                .thenReturn(mockLowRiskPrediction());

        String accountId = "ACC-015";

        // When - simulate concurrent transactions
        Thread thread1 = new Thread(() -> {
            Transaction transaction = createTransaction(accountId, BigDecimal.valueOf(100.00));
            RiskAssessment assessment = riskScoringService.assessRisk(transaction);
            assertThat(assessment).isNotNull();
        });

        Thread thread2 = new Thread(() -> {
            Transaction transaction = createTransaction(accountId, BigDecimal.valueOf(200.00));
            RiskAssessment assessment = riskScoringService.assessRisk(transaction);
            assertThat(assessment).isNotNull();
        });

        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();

        // Then - both assessments should complete successfully
    }

    @Test
    @Order(16)
    @DisplayName("Should assess risk for different transaction types")
    void shouldAssessRiskForDifferentTransactionTypes() {
        when(mlServicePort.predict(any(Transaction.class)))
                .thenReturn(mockLowRiskPrediction());

        for (TransactionType type : TransactionType.values()) {
            Transaction transaction = createTransactionWithType("ACC-TYPE-" + type.name(), type);
            RiskAssessment assessment = riskScoringService.assessRisk(transaction);

            assertThat(assessment).isNotNull();
            assertThat(assessment.getMlPrediction()).isNotNull();
        }
    }

    @Test
    @Order(17)
    @DisplayName("Should assess risk for different channels")
    void shouldAssessRiskForDifferentChannels() {
        when(mlServicePort.predict(any(Transaction.class)))
                .thenReturn(mockLowRiskPrediction());

        for (Channel channel : Channel.values()) {
            Transaction transaction = createTransactionWithChannel("ACC-CHANNEL-" + channel.name(), channel);
            RiskAssessment assessment = riskScoringService.assessRisk(transaction);

            assertThat(assessment).isNotNull();
            assertThat(assessment.getMlPrediction()).isNotNull();
        }
    }

    @Test
    @Order(18)
    @DisplayName("Should preserve ML prediction in assessment")
    void shouldPreserveMLPredictionInAssessment() {
        when(mlServicePort.predict(any(Transaction.class)))
                .thenReturn(mockLowRiskPrediction());

        Transaction transaction = createTransaction("ACC-018", BigDecimal.valueOf(100.00));

        // When
        RiskAssessment assessment = riskScoringService.assessRisk(transaction);

        // Then
        assertThat(assessment.getMlPrediction()).isNotNull();
        assertThat(assessment.getMlPrediction().modelId()).isEqualTo("test-model");
        assertThat(assessment.getMlPrediction().modelVersion()).isEqualTo("1.0.0");
        assertThat(assessment.getMlPrediction().fraudProbability()).isEqualTo(0.15);
        assertThat(assessment.getMlPrediction().confidence()).isEqualTo(0.95);
        assertThat(assessment.getMlPrediction().featureImportance()).isNotEmpty();
    }

    @Test
    @Order(19)
    @DisplayName("Should assess risk with same location transactions")
    void shouldAssessRiskWithSameLocationTransactions() {
        when(mlServicePort.predict(any(Transaction.class)))
                .thenReturn(mockLowRiskPrediction());

        String accountId = "ACC-019";
        Location location = new Location(40.7128, -74.0060, "USA", "New York", Instant.now());

        Transaction transaction1 = createTransactionWithLocation(accountId, location);
        transactionRepository.save(transaction1);

        Transaction transaction2 = createTransactionWithLocation(accountId, location);

        // When
        RiskAssessment assessment = riskScoringService.assessRisk(transaction2);

        // Then
        assertThat(assessment).isNotNull();
        // No impossible travel should be detected
        assertThat(assessment.getRuleEvaluations())
                .extracting(RuleEvaluation::ruleName)
                .doesNotContain("Impossible Travel");
    }

    @Test
    @Order(20)
    @DisplayName("Should assess risk with reasonable travel distance")
    void shouldAssessRiskWithReasonableTravelDistance() {
        when(mlServicePort.predict(any(Transaction.class)))
                .thenReturn(mockLowRiskPrediction());

        String accountId = "ACC-020";
        Instant now = Instant.now();

        // Transaction in New York 10 hours ago
        Location location1 = new Location(40.7128, -74.0060, "USA", "New York", now.minusSeconds(36000));
        Transaction transaction1 = createTransactionWithLocation(accountId, location1);
        transactionRepository.save(transaction1);

        // Transaction in Los Angeles now (reasonable time for travel)
        Location location2 = new Location(34.0522, -118.2437, "USA", "Los Angeles", now);
        Transaction transaction2 = createTransactionWithLocation(accountId, location2);

        // When
        RiskAssessment assessment = riskScoringService.assessRisk(transaction2);

        // Then
        assertThat(assessment).isNotNull();
        // No impossible travel should be detected for reasonable speed
        assertThat(assessment.getRuleEvaluations())
                .extracting(RuleEvaluation::ruleName)
                .doesNotContain("Impossible Travel");
    }

    // Helper methods

    private MLPrediction mockLowRiskPrediction() {
        return new MLPrediction(
                "test-model",
                "1.0.0",
                0.15,
                0.95,
                Map.of("amount", 0.3, "velocity", 0.2)
        );
    }

    private Transaction createTransaction(String accountId, BigDecimal amount) {
        return Transaction.builder()
                .id(TransactionId.generate())
                .accountId(accountId)
                .amount(new Money(amount, Currency.getInstance("USD")))
                .type(TransactionType.PURCHASE)
                .channel(Channel.ONLINE)
                .merchant(new Merchant(MerchantId.of("MERCH-001"), "Test Merchant", "Retail"))
                .location(new Location(40.7128, -74.0060, "USA", "New York", Instant.now()))
                .deviceId("DEVICE-001")
                .timestamp(Instant.now())
                .build();
    }

    private Transaction createTransactionWithLocation(String accountId, Location location) {
        return Transaction.builder()
                .id(TransactionId.generate())
                .accountId(accountId)
                .amount(new Money(BigDecimal.valueOf(100.00), Currency.getInstance("USD")))
                .type(TransactionType.PURCHASE)
                .channel(Channel.ONLINE)
                .merchant(new Merchant(MerchantId.of("MERCH-001"), "Test Merchant", "Retail"))
                .location(location)
                .deviceId("DEVICE-001")
                .timestamp(location.timestamp())
                .build();
    }

    private Transaction createTransactionWithLocationAndAmount(String accountId, Location location, BigDecimal amount) {
        return Transaction.builder()
                .id(TransactionId.generate())
                .accountId(accountId)
                .amount(new Money(amount, Currency.getInstance("USD")))
                .type(TransactionType.PURCHASE)
                .channel(Channel.ONLINE)
                .merchant(new Merchant(MerchantId.of("MERCH-001"), "Test Merchant", "Retail"))
                .location(location)
                .deviceId("DEVICE-001")
                .timestamp(location.timestamp())
                .build();
    }

    private Transaction createTransactionWithType(String accountId, TransactionType type) {
        return Transaction.builder()
                .id(TransactionId.generate())
                .accountId(accountId)
                .amount(new Money(BigDecimal.valueOf(100.00), Currency.getInstance("USD")))
                .type(type)
                .channel(Channel.ONLINE)
                .merchant(new Merchant(MerchantId.of("MERCH-001"), "Test Merchant", "Retail"))
                .location(new Location(40.7128, -74.0060, "USA", "New York", Instant.now()))
                .deviceId("DEVICE-001")
                .timestamp(Instant.now())
                .build();
    }

    private Transaction createTransactionWithChannel(String accountId, Channel channel) {
        return Transaction.builder()
                .id(TransactionId.generate())
                .accountId(accountId)
                .amount(new Money(BigDecimal.valueOf(100.00), Currency.getInstance("USD")))
                .type(TransactionType.PURCHASE)
                .channel(channel)
                .merchant(new Merchant(MerchantId.of("MERCH-001"), "Test Merchant", "Retail"))
                .location(new Location(40.7128, -74.0060, "USA", "New York", Instant.now()))
                .deviceId("DEVICE-001")
                .timestamp(Instant.now())
                .build();
    }
}