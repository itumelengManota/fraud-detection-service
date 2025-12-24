package com.twenty9ine.frauddetection.application.service;

import com.twenty9ine.frauddetection.application.dto.LocationDto;
import com.twenty9ine.frauddetection.application.dto.RiskAssessmentDto;
import com.twenty9ine.frauddetection.application.port.in.command.ProcessTransactionCommand;
import com.twenty9ine.frauddetection.application.port.in.query.GetRiskAssessmentQuery;
import com.twenty9ine.frauddetection.application.port.out.MLServicePort;
import com.twenty9ine.frauddetection.application.port.out.RiskAssessmentRepository;
import com.twenty9ine.frauddetection.application.port.out.TransactionRepository;
import com.twenty9ine.frauddetection.application.port.out.VelocityServicePort;
import com.twenty9ine.frauddetection.domain.valueobject.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ActiveProfiles("test")
@DisabledInAotMode
@SpringBootTest
@Testcontainers
@DisplayName("ProcessTransactionApplicationService Integration Tests")
@Execution(ExecutionMode.SAME_THREAD)
class ProcessTransactionApplicationServiceIntegrationTest {

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
        // Force container startup by accessing a property immediately
        postgres.start();
        redis.start();
        kafka.start();
        schemaRegistry.start();

        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);

        String apicurioUrl = "http://" + schemaRegistry.getHost() + ":" + schemaRegistry.getFirstMappedPort() + "/apis/registry/v2";
        registry.add("apicurio.registry.url", () -> apicurioUrl);
        registry.add("spring.kafka.consumer.properties.apicurio.registry.url", () -> apicurioUrl);
        registry.add("spring.kafka.producer.properties.apicurio.registry.url", () -> apicurioUrl);

        registry.add("aws.sagemaker.enabled", () -> "false");
    }

    @Autowired
    private ProcessTransactionApplicationService processTransactionService;

    @Autowired
    private FraudDetectionApplicationService fraudDetectionService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private RiskAssessmentRepository riskAssessmentRepository;

    @Autowired
    private VelocityServicePort velocityService;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private CacheManager cacheManager;

    @MockitoBean
    private MLServicePort mlServicePort;

    @BeforeEach
    void setUp() {
        // Clear database tables
//        transactionRepository.findAll().forEach(tx ->
//                transactionRepository.deleteById(tx.id())
//        );
//
//        riskAssessmentRepository.findAll().forEach(ra ->
//                riskAssessmentRepository.deleteById(ra.id())
//        );

        // Clear Redis velocity counters
        redissonClient.getKeys().deleteByPattern("velocity:*");

        // Clear all Spring caches
        cacheManager.getCacheNames().forEach(cacheName -> {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        });
    }

    @Nested
    @DisplayName("Transaction Processing Scenarios")
    class TransactionProcessingScenarios {

        @Test
        @DisplayName("Should successfully process and persist a valid transaction")
        void shouldProcessAndPersistTransaction() {
            // Given
            when(mlServicePort.predict(any(Transaction.class)))
                    .thenReturn(mockLowRiskPrediction());

            UUID transactionId = UUID.randomUUID();
            ProcessTransactionCommand command = buildStandardCommand(transactionId);

            // When
            processTransactionService.process(command);

            // Then
            Transaction savedTransaction = transactionRepository.findById(TransactionId.of(transactionId)).orElse(null);
            assertThat(savedTransaction).isNotNull();
            assertThat(savedTransaction.id().toUUID()).isEqualTo(transactionId);
            assertThat(savedTransaction.accountId()).isEqualTo(command.accountId());
            assertThat(savedTransaction.amount().value()).isEqualByComparingTo(command.amount());
            assertThat(savedTransaction.type()).isEqualTo(TransactionType.fromString(command.type()));
            assertThat(savedTransaction.channel()).isEqualTo(Channel.fromString(command.channel()));
        }

        @Test
        @DisplayName("Should trigger risk assessment when processing transaction")
        void shouldTriggerRiskAssessment() {
            // Given
            when(mlServicePort.predict(any(Transaction.class)))
                    .thenReturn(mockMediumRiskPrediction());

            UUID transactionId = UUID.randomUUID();
            ProcessTransactionCommand command = buildStandardCommand(transactionId);

            // When
            processTransactionService.process(command);

            // Then
            RiskAssessmentDto assessment = fraudDetectionService.get(new GetRiskAssessmentQuery(transactionId));
            assertThat(assessment).isNotNull();
            assertThat(assessment.transactionId()).isEqualTo(transactionId);
            assertThat(assessment.riskScore()).isEqualTo(27);
            assertThat(assessment.decision()).isEqualTo(Decision.ALLOW);
        }

        @Test
        @DisplayName("Should increment velocity counters after processing")
        void shouldIncrementVelocityCounters() {
            // Given
            when(mlServicePort.predict(any(Transaction.class)))
                    .thenReturn(mockLowRiskPrediction());

            UUID transactionId = UUID.randomUUID();
            ProcessTransactionCommand command = buildCommandForAccount("ACC-VEL-TEST-001", transactionId);

            // When
            processTransactionService.process(command);

            // Then
            Transaction transaction = transactionRepository.findById(TransactionId.of(transactionId)).orElseThrow();
            VelocityMetrics metrics = velocityService.findVelocityMetricsByTransaction(transaction);

            assertThat(metrics).isNotNull();
            assertThat(metrics.getTransactionCount(TimeWindow.FIVE_MINUTES)).isEqualTo(1);
            assertThat(metrics.getTotalAmount(TimeWindow.FIVE_MINUTES)).isEqualByComparingTo(transaction.amount().value());
        }

        @Test
        @DisplayName("Should process transaction with location data")
        void shouldProcessTransactionWithLocation() {
            // Given
            when(mlServicePort.predict(any(Transaction.class)))
                    .thenReturn(mockLowRiskPrediction());

            UUID transactionId = UUID.randomUUID();
            LocationDto location = new LocationDto(-33.9249, 18.4241, "South Africa", "Cape Town", Instant.now());
            ProcessTransactionCommand command = buildCommandWithLocation(transactionId, location);

            // When
            processTransactionService.process(command);

            // Then
            Transaction savedTransaction = transactionRepository.findById(TransactionId.of(transactionId)).orElseThrow();
            assertThat(savedTransaction.location()).isNotNull();
            assertThat(savedTransaction.location().latitude()).isEqualTo(location.latitude());
            assertThat(savedTransaction.location().longitude()).isEqualTo(location.longitude());
            assertThat(savedTransaction.location().country()).isEqualTo(location.country());
            assertThat(savedTransaction.location().city()).isEqualTo(location.city());
        }

    }

    @Nested
    @DisplayName("Multiple Transaction Processing")
    class MultipleTransactionProcessing {

        @Test
        @DisplayName("Should process multiple transactions for same account")
        void shouldProcessMultipleTransactionsForAccount() {
            // Given
            when(mlServicePort.predict(any(Transaction.class)))
                    .thenReturn(mockLowRiskPrediction());

            String accountId = "ACC-MULTI-001";
            List<UUID> transactionIds = new ArrayList<>();

            // When
            for (int i = 0; i < 5; i++) {
                transactionIds.add(UUID.randomUUID());
                processTransactionService.process(buildCommandForAccount(accountId, transactionIds.get(i)));
            }

            // Then
            List<Transaction> accountTransactions = transactionRepository.findByAccountId(accountId);
            assertThat(accountTransactions).hasSize(5);
            assertThat(accountTransactions)
                    .extracting(transaction -> transaction.id().toUUID())
                    .containsExactlyInAnyOrderElementsOf(transactionIds);
        }

        @Test
        @DisplayName("Should accumulate velocity metrics across transactions")
        void shouldAccumulateVelocityMetrics() {
            // Given
            when(mlServicePort.predict(any(Transaction.class)))
                    .thenReturn(mockLowRiskPrediction());

            String accountId = "ACC-VEL-ACCUM-001";
            BigDecimal transactionAmount = new BigDecimal("100.00");
            final int NUMBER_OF_TRANSACTIONS = 3;

            // When
            UUID lastTransactionId = null;

            for (int i = 0; i < NUMBER_OF_TRANSACTIONS; i++) {
                lastTransactionId = UUID.randomUUID();
                processTransactionService.process(buildCommandWithAmount(accountId, lastTransactionId, transactionAmount));
            }

            // Then
            Transaction lastTransaction = transactionRepository.findById(TransactionId.of(lastTransactionId)).orElseThrow();
            VelocityMetrics metrics = velocityService.findVelocityMetricsByTransaction(lastTransaction);

            assertThat(metrics.getTransactionCount(TimeWindow.FIVE_MINUTES)).isEqualTo(NUMBER_OF_TRANSACTIONS);
            assertThat(metrics.getTotalAmount(TimeWindow.FIVE_MINUTES))
                    .isEqualByComparingTo(transactionAmount.multiply(BigDecimal.valueOf(NUMBER_OF_TRANSACTIONS)));
        }
    }

    @Nested
    @DisplayName("Transaction Types and Channels")
    class TransactionTypesAndChannels {

        @Test
        @DisplayName("Should process PURCHASE transaction")
        void shouldProcessPurchaseTransaction() {
            // Given
            when(mlServicePort.predict(any(Transaction.class)))
                    .thenReturn(mockLowRiskPrediction());

            UUID transactionId = UUID.randomUUID();
            ProcessTransactionCommand command = buildCommandWithType(transactionId, "PURCHASE");

            // When
            processTransactionService.process(command);

            // Then
            Transaction savedTransaction = transactionRepository.findById(TransactionId.of(transactionId)).orElseThrow();
            assertThat(savedTransaction.type()).isEqualTo(TransactionType.PURCHASE);
        }

        @Test
        @DisplayName("Should process TRANSFER transaction")
        void shouldProcessTransferTransaction() {
            // Given
            when(mlServicePort.predict(any(Transaction.class)))
                    .thenReturn(mockMediumRiskPrediction());

            UUID transactionId = UUID.randomUUID();
            ProcessTransactionCommand command = buildCommandWithType(transactionId, "TRANSFER");

            // When
            processTransactionService.process(command);

            // Then
            Transaction savedTransaction = transactionRepository.findById(TransactionId.of(transactionId)).orElseThrow();
            assertThat(savedTransaction.type()).isEqualTo(TransactionType.TRANSFER);
        }

        @Test
        @DisplayName("Should process transaction through MOBILE channel")
        void shouldProcessMobileChannelTransaction() {
            // Given
            when(mlServicePort.predict(any(Transaction.class)))
                    .thenReturn(mockLowRiskPrediction());

            UUID transactionId = UUID.randomUUID();
            ProcessTransactionCommand command = buildCommandWithChannel(transactionId, "MOBILE");

            // When
            processTransactionService.process(command);

            // Then
            Transaction savedTransaction = transactionRepository.findById(TransactionId.of(transactionId)).orElseThrow();
            assertThat(savedTransaction.channel()).isEqualTo(Channel.MOBILE);
        }

        @Test
        @DisplayName("Should process transaction through ONLINE channel")
        void shouldProcessOnlineChannelTransaction() {
            // Given
            when(mlServicePort.predict(any(Transaction.class)))
                    .thenReturn(mockLowRiskPrediction());

            UUID transactionId = UUID.randomUUID();
            ProcessTransactionCommand command = buildCommandWithChannel(transactionId, "ONLINE");

            // When
            processTransactionService.process(command);

            // Then
            Transaction savedTransaction = transactionRepository.findById(TransactionId.of(transactionId)).orElseThrow();
            assertThat(savedTransaction.channel()).isEqualTo(Channel.ONLINE);
        }
    }

    // Helper methods
    private MLPrediction mockLowRiskPrediction() {
        return new MLPrediction("test-endpoint", "1.0.0", 0.15, 0.95, Map.of("amount", 0.3));
    }

    private MLPrediction mockMediumRiskPrediction() {
        return new MLPrediction("test-endpoint", "1.0.0", 0.45, 0.92, Map.of("amount", 0.5));
    }

    private ProcessTransactionCommand buildStandardCommand(UUID transactionId) {
        return ProcessTransactionCommand.builder()
                .transactionId(transactionId)
                .accountId("ACC-STD-001")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .type("PURCHASE")
                .channel("MOBILE")
                .merchantId("MER-001")
                .merchantName("Test Merchant")
                .merchantCategory("RETAIL")
                .location(new LocationDto(-25.7479, 28.2293, "South Africa", "Pretoria", Instant.now()))
                .deviceId("DEV-001")
                .transactionTimestamp(Instant.now())
                .build();
    }

    private ProcessTransactionCommand buildCommandForAccount(String accountId, UUID transactionId) {
        return ProcessTransactionCommand.builder()
                .transactionId(transactionId)
                .accountId(accountId)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .type("PURCHASE")
                .channel("MOBILE")
                .merchantId("MER-001")
                .merchantName("Test Merchant")
                .merchantCategory("RETAIL")
                .location(new LocationDto(-25.7479, 28.2293, "South Africa", "Pretoria", Instant.now()))
                .deviceId("DEV-001")
                .transactionTimestamp(Instant.now())
                .build();
    }

    private ProcessTransactionCommand buildCommandWithLocation(UUID transactionId, LocationDto location) {
        return ProcessTransactionCommand.builder()
                .transactionId(transactionId)
                .accountId("ACC-LOC-001")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .type("PURCHASE")
                .channel("MOBILE")
                .merchantId("MER-001")
                .merchantName("Test Merchant")
                .merchantCategory("RETAIL")
                .location(location)
                .deviceId("DEV-001")
                .transactionTimestamp(Instant.now())
                .build();
    }

    private ProcessTransactionCommand buildCommandWithAmount(String accountId, UUID transactionId, BigDecimal amount) {
        return ProcessTransactionCommand.builder()
                .transactionId(transactionId)
                .accountId(accountId)
                .amount(amount)
                .currency("USD")
                .type("PURCHASE")
                .channel("MOBILE")
                .merchantId("MER-001")
                .merchantName("Test Merchant")
                .merchantCategory("RETAIL")
                .location(new LocationDto(-25.7479, 28.2293, "South Africa", "Pretoria", Instant.now()))
                .deviceId("DEV-001")
                .transactionTimestamp(Instant.now())
                .build();
    }

    private ProcessTransactionCommand buildCommandWithType(UUID transactionId, String type) {
        return ProcessTransactionCommand.builder()
                .transactionId(transactionId)
                .accountId("ACC-TYPE-001")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .type(type)
                .channel("MOBILE")
                .merchantId("MER-001")
                .merchantName("Test Merchant")
                .merchantCategory("RETAIL")
                .location(new LocationDto(-25.7479, 28.2293, "South Africa", "Pretoria", Instant.now()))
                .deviceId("DEV-001")
                .transactionTimestamp(Instant.now())
                .build();
    }

    private ProcessTransactionCommand buildCommandWithChannel(UUID transactionId, String channel) {
        return ProcessTransactionCommand.builder()
                .transactionId(transactionId)
                .accountId("ACC-CHAN-001")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .type("PURCHASE")
                .channel(channel)
                .merchantId("MER-001")
                .merchantName("Test Merchant")
                .merchantCategory("RETAIL")
                .location(new LocationDto(-25.7479, 28.2293, "South Africa", "Pretoria", Instant.now()))
                .deviceId("DEV-001")
                .transactionTimestamp(Instant.now())
                .build();
    }
}