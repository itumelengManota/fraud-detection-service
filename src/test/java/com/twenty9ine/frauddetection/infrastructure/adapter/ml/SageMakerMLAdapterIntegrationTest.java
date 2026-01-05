package com.twenty9ine.frauddetection.infrastructure.adapter.ml;

import com.twenty9ine.frauddetection.application.port.out.AccountServicePort;
import com.twenty9ine.frauddetection.application.port.out.MLServicePort;
import com.twenty9ine.frauddetection.application.port.out.TransactionRepository;
import com.twenty9ine.frauddetection.domain.valueobject.*;
import com.twenty9ine.frauddetection.infrastructure.config.RedisConfig;
import com.twenty9ine.frauddetection.infrastructure.config.SageMakerConfig;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        classes = {SageMakerMLAdapter.class, SageMakerConfig.class, RedisConfig.class, SageMakerMLAdapterIntegrationTest.SageMakerMLAdapterTestConfig.class}
)
@DisabledInAotMode
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("SageMakerMLAdapter Integration Tests")
class SageMakerMLAdapterIntegrationTest {

    private static final String ACCOUNT_ID = "ACC-12345-1";

    @TestConfiguration
    static class SageMakerMLAdapterTestConfig {
        @Bean
        public JsonMapper jsonMapper() {
            return new JsonMapper();
        }

        @Bean
        public LettuceConnectionFactory lettuceConnectionFactory() {
            return new LettuceConnectionFactory(redis.getHost(), redis.getFirstMappedPort());
        }

        @Bean
        public CircuitBreakerRegistry circuitBreakerRegistry() {
            CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                    .slidingWindowSize(20)
                    .minimumNumberOfCalls(10)
                    .failureRateThreshold(50)
                    .waitDurationInOpenState(java.time.Duration.ofSeconds(60))
                    .build();

            return CircuitBreakerRegistry.of(config);
        }

        @Bean
        public RetryRegistry retryRegistry() {
            RetryConfig config = RetryConfig.custom()
                    .maxAttempts(2)
                    .waitDuration(java.time.Duration.ofMillis(50))
                    .build();

            return RetryRegistry.of(config);
        }

        @Bean
        public TimeLimiterRegistry timeLimiterRegistry() {
            TimeLimiterConfig config = TimeLimiterConfig.custom()
                    .timeoutDuration(java.time.Duration.ofMillis(500))
                    .build();

            return TimeLimiterRegistry.of(config);
        }

        @Bean
        public BulkheadRegistry bulkheadRegistry() {
            BulkheadConfig config = BulkheadConfig.custom()
                    .maxConcurrentCalls(50)
                    .maxWaitDuration(java.time.Duration.ofMillis(25))
                    .build();

            return BulkheadRegistry.of(config);
        }
    }

    @Container
    static GenericContainer<?> mockoon = new GenericContainer<>(DockerImageName.parse("mockoon/cli:latest"))
            .withExposedPorts(3000)
            .withClasspathResourceMapping("mockoon/data-test.json", "/data/data.json", BindMode.READ_ONLY)
            .withCommand("--data", "/data/data.json")
            .withReuse(true);

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .withReuse(true);

    @Autowired
    private MLServicePort sageMakerMLAdapter;

    @Autowired
    private CacheManager cacheManager;

    @MockitoBean
    private AccountServicePort accountServicePort;

    @MockitoBean
    private TransactionRepository transactionRepository;

    private Locale originalLocale;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        mockoon.start();
        redis.start();

        // Account Service
        registry.add("account-service.base-url", SageMakerMLAdapterIntegrationTest::buildAccountServiceUrl);

        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);

        registry.add("spring.flyway.enabled", () -> false);

        registry.add("aws.sagemaker.enabled", () -> true);
        registry.add("aws.sagemaker.endpoint-name", () -> "fraud-detection-endpoint");
        registry.add("aws.sagemaker.model-version", () -> "1.0.0");
    }

    private static String buildAccountServiceUrl() {
        return "http://%s:%s".formatted(mockoon.getHost(), mockoon.getFirstMappedPort());
    }

    @BeforeEach
    void setUp() {
        clearCache();
        setupDefaultMocks();
        setupLocale();
    }

    @AfterEach
    void tearDown() {
        restoreLocale();
    }

    @Nested
    @DisplayName("Categorical Feature Encoding")
    class CategoricalFeatureEncoding {

        @Test
        @DisplayName("Should correctly encode merchant categories")
        void shouldEncodeAllMerchantCategories() {
            // Test all merchant categories: GROCERY=0, RESTAURANT=1, GAS=2, RETAIL=3, 
            // ENTERTAINMENT=4, ELECTRONICS=5, JEWELRY=6, CRYPTO=7, GIFT_CARDS=8, GAMBLING=9
            MerchantCategory[] categories = MerchantCategory.values();

            for (MerchantCategory category : categories) {
                Transaction transaction = createTransactionWithMerchant(ACCOUNT_ID, new Merchant(MerchantId.of("MERCH-" + category), "Test Merchant", category));
                MLPrediction result = sageMakerMLAdapter.predict(transaction);
                assertThat(result).isNotNull();
            }
        }

        @Test
        @DisplayName("Should correctly encode transaction types")
        void shouldEncodeAllTransactionTypes() {
            // Test all transaction types: PURCHASE=0, ATM_WITHDRAWAL=1, TRANSFER=2, PAYMENT=3, REFUND=4
            TransactionType[] types = TransactionType.values();

            for (TransactionType type : types) {
                Transaction transaction = createTransactionWithType(ACCOUNT_ID, type);
                MLPrediction result = sageMakerMLAdapter.predict(transaction);
                assertThat(result).isNotNull();
            }
        }

        @Test
        @DisplayName("Should correctly encode all channels")
        void shouldEncodeAllChannels() {
            // Test all channels: CARD=0, ONLINE=1, MOBILE=2, POS=3, ATM=4
            Channel[] channels = Channel.values();

            for (Channel channel : channels) {
                Transaction transaction = createTransactionWithChannel(ACCOUNT_ID, channel);
                MLPrediction result = sageMakerMLAdapter.predict(transaction);
                assertThat(result).isNotNull();
            }
        }
    }

    @Nested
    @DisplayName("Fraud Pattern Detection Based on Training Data")
    class FraudPatternDetection {

        @Test
        @DisplayName("Should detect high amounts as risk factor")
        void shouldDetectHighAmountAsRiskFactor() {
            // Training data uses lognormal(mean=6, sigma=1.8) for fraud vs lognormal(mean=4, sigma=1.5) for legit
            Transaction lowAmountTx = createTransaction(ACCOUNT_ID, BigDecimal.valueOf(50.00));
            Transaction highAmountTx = createTransaction(ACCOUNT_ID, BigDecimal.valueOf(5000.00));

            MLPrediction lowAmountPrediction = sageMakerMLAdapter.predict(lowAmountTx);
            MLPrediction highAmountPrediction = sageMakerMLAdapter.predict(highAmountTx);

            assertThat(highAmountPrediction.fraudProbability()).isGreaterThan(lowAmountPrediction.fraudProbability());
        }

        @Test
        @DisplayName("Should detect night-time transactions as higher risk")
        void shouldDetectNightTimeTransactions() {
            // Fraud occurs more at night (hours 0-6, 23)
            Instant nightTime = Instant.parse("2024-01-15T02:00:00Z"); // 2 AM
            Instant dayTime = Instant.parse("2024-01-15T14:00:00Z"); // 2 PM

            Transaction nightTransaction = createTransactionAtTime(ACCOUNT_ID, nightTime);
            Transaction dayTransaction = createTransactionAtTime(ACCOUNT_ID, dayTime);

            MLPrediction nightPrediction = sageMakerMLAdapter.predict(nightTransaction);
            MLPrediction dayPrediction = sageMakerMLAdapter.predict(dayTransaction);

            assertThat(nightPrediction.fraudProbability()).isGreaterThan(dayPrediction.fraudProbability());
        }

        @Test
        @DisplayName("Should detect high transaction velocity as risk factor")
        void shouldDetectHighTransactionVelocity() {
            // Training: legitimate ~2 transactions/24h, fraud ~8 transactions/24h
            Transaction lowVelocityTx = createTransaction(ACCOUNT_ID, BigDecimal.valueOf(100.00));
            Transaction highVelocityTx = createTransaction(ACCOUNT_ID, BigDecimal.valueOf(100.00));

            // Mock low velocity
            when(transactionRepository.findByAccountIdAndTimestampBetween(anyString(), any(), any()))
                    .thenReturn(List.of(createTransaction(ACCOUNT_ID, BigDecimal.valueOf(50.00))));
            MLPrediction lowVelocityPrediction = sageMakerMLAdapter.predict(lowVelocityTx);

            // Mock high velocity (8+ transactions)
            when(transactionRepository.findByAccountIdAndTimestampBetween(anyString(), any(), any()))
                    .thenReturn(createTransactionHistory(ACCOUNT_ID, 10));
            MLPrediction highVelocityPrediction = sageMakerMLAdapter.predict(highVelocityTx);

            assertThat(highVelocityPrediction.fraudProbability()).isGreaterThan(lowVelocityPrediction.fraudProbability());
        }

        @Test
        @DisplayName("Should detect international transactions as higher risk")
        void shouldDetectInternationalTransactions() {
            // Training: legitimate 95% domestic, fraud 40% domestic
            Transaction domesticTx = createTransaction(ACCOUNT_ID, BigDecimal.valueOf(200.00));
            Transaction internationalTx = createForeignTransaction(ACCOUNT_ID);

            MLPrediction domesticPrediction = sageMakerMLAdapter.predict(domesticTx);
            MLPrediction internationalPrediction = sageMakerMLAdapter.predict(internationalTx);

            assertThat(internationalPrediction.fraudProbability()).isGreaterThan(domesticPrediction.fraudProbability());
        }

        @Test
        @DisplayName("Should detect missing device as risk factor")
        void shouldDetectMissingDevice() {
            // Training: legitimate ~100% has device, fraud ~70% has device
            Transaction withDevice = createTransactionWithDevice(ACCOUNT_ID, "DEVICE-123");
            Transaction withoutDevice = createTransactionWithDevice(ACCOUNT_ID, null);

            MLPrediction withDevicePrediction = sageMakerMLAdapter.predict(withDevice);
            MLPrediction withoutDevicePrediction = sageMakerMLAdapter.predict(withoutDevice);

            assertThat(withoutDevicePrediction.fraudProbability()).isGreaterThan(withDevicePrediction.fraudProbability());
        }

        @Test
        @DisplayName("Should detect new merchant as risk factor")
        void shouldDetectNewMerchant() {
            Transaction transaction1 = createTransaction(ACCOUNT_ID, BigDecimal.valueOf(300.00));
            Transaction transaction2 = createTransaction(ACCOUNT_ID, BigDecimal.valueOf(300.00));

            // Known merchant
            when(transactionRepository.findByAccountIdAndTimestampBetween(anyString(), any(), any()))
                    .thenReturn(List.of(createTransactionWithSameMerchant(ACCOUNT_ID)));
            MLPrediction knownMerchantPrediction = sageMakerMLAdapter.predict(transaction1);

            // New merchant
            when(transactionRepository.findByAccountIdAndTimestampBetween(anyString(), any(), any()))
                    .thenReturn(List.of(createTransactionWithMerchant(ACCOUNT_ID, new Merchant(MerchantId.of("DIFF-MERCH"), "Different Merchant", MerchantCategory.RETAIL))));
            MLPrediction newMerchantPrediction = sageMakerMLAdapter.predict(transaction2);

            assertThat(newMerchantPrediction.fraudProbability()).isGreaterThan(knownMerchantPrediction.fraudProbability());
        }

        @Test
        @DisplayName("Should detect large distance from home as risk factor")
        void shouldDetectLargeDistanceFromHome() {
            // Training: legitimate gamma(2, 10), fraud gamma(5, 50)
            Transaction nearHomeTx = createTransaction(ACCOUNT_ID, BigDecimal.valueOf(100.00));
            Transaction farFromHomeTx = createTransactionAtLocation(
                    ACCOUNT_ID,
                    Location.of(40.7128, -74.0060, "US", "New York") // Far from Johannesburg
            );

            MLPrediction nearHomePrediction = sageMakerMLAdapter.predict(nearHomeTx);
            MLPrediction farFromHomePrediction = sageMakerMLAdapter.predict(farFromHomeTx);

            assertThat(farFromHomePrediction.fraudProbability()).isGreaterThan(nearHomePrediction.fraudProbability());
        }

        @Test
        @DisplayName("Should detect weekend transaction pattern")
        void shouldDetectWeekendPattern() {
            // Training: legitimate ~28% weekend, fraud ~50% weekend
            Instant weekday = Instant.parse("2024-01-15T14:00:00Z"); // Monday
            Instant weekend = Instant.parse("2024-01-13T14:00:00Z"); // Saturday

            Transaction weekdayTx = createTransactionAtTime(ACCOUNT_ID, weekday);
            Transaction weekendTx = createTransactionAtTime(ACCOUNT_ID, weekend);

            MLPrediction weekdayPrediction = sageMakerMLAdapter.predict(weekdayTx);
            MLPrediction weekendPrediction = sageMakerMLAdapter.predict(weekendTx);

            assertThat(weekendPrediction.fraudProbability()).isGreaterThanOrEqualTo(weekdayPrediction.fraudProbability());
        }

        @Test
        @DisplayName("Should detect high amount in last 24 hours")
        void shouldDetectHighAmountInLast24Hours() {
            // Training: legitimate lognormal(5,1), fraud lognormal(7,1.5)
            Transaction transaction1 = createTransaction(ACCOUNT_ID, BigDecimal.valueOf(500.00));
            Transaction transaction2 = createTransaction(ACCOUNT_ID, BigDecimal.valueOf(500.00));

            // Low spending pattern
            when(transactionRepository.findByAccountIdAndTimestampBetween(anyString(), any(), any()))
                    .thenReturn(List.of(
                            createTransaction(ACCOUNT_ID, BigDecimal.valueOf(20.00)),
                            createTransaction(ACCOUNT_ID, BigDecimal.valueOf(30.00))
                    ));
            MLPrediction lowSpendingPrediction = sageMakerMLAdapter.predict(transaction1);

            // High spending pattern
            when(transactionRepository.findByAccountIdAndTimestampBetween(anyString(), any(), any()))
                    .thenReturn(List.of(
                            createTransaction(ACCOUNT_ID, BigDecimal.valueOf(2000.00)),
                            createTransaction(ACCOUNT_ID, BigDecimal.valueOf(1500.00)),
                            createTransaction(ACCOUNT_ID, BigDecimal.valueOf(1000.00))
                    ));
            MLPrediction highSpendingPrediction = sageMakerMLAdapter.predict(transaction2);

            assertThat(highSpendingPrediction.fraudProbability()).isGreaterThan(lowSpendingPrediction.fraudProbability());
        }
    }

    @Nested
    @DisplayName("Combined Risk Factors")
    class CombinedRiskFactors {

        @Test
        @DisplayName("Should detect multiple fraud indicators")
        void shouldDetectMultipleFraudIndicators() {
            // Combine multiple risk factors
            Transaction highRiskTx = createComplexFraudTransaction(ACCOUNT_ID);
            Transaction lowRiskTx = createComplexLegitimateTransaction(ACCOUNT_ID);

            MLPrediction highRiskPrediction = sageMakerMLAdapter.predict(highRiskTx);
            MLPrediction lowRiskPrediction = sageMakerMLAdapter.predict(lowRiskTx);

            assertThat(highRiskPrediction.fraudProbability()).isGreaterThan(0.9);
            assertThat(lowRiskPrediction.fraudProbability()).isLessThan(0.3);
        }
    }

    @Nested
    @DisplayName("Edge Cases and Boundary Conditions")
    class EdgeCasesAndBoundaryConditions {

        @Test
        @DisplayName("Should handle zero amount transaction")
        void shouldHandleZeroAmount() {
            Transaction transaction = createTransaction(ACCOUNT_ID, BigDecimal.ZERO);
            MLPrediction result = sageMakerMLAdapter.predict(transaction);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should handle very large amount transaction")
        void shouldHandleVeryLargeAmount() {
            Transaction transaction = createTransaction(ACCOUNT_ID, BigDecimal.valueOf(1_000_000.00));
            MLPrediction result = sageMakerMLAdapter.predict(transaction);
            assertThat(result).isNotNull();
            assertThat(result.fraudProbability()).isGreaterThan(0.3);
        }

        @Test
        @DisplayName("Should handle all day hours (0-23)")
        void shouldHandleAllDayHours() {
            for (int hour = 0; hour < 24; hour++) {
                Instant timestamp = Instant.parse("2024-01-15T00:00:00Z").plus(hour, ChronoUnit.HOURS);
                Transaction transaction = createTransactionAtTime(ACCOUNT_ID, timestamp);
                MLPrediction result = sageMakerMLAdapter.predict(transaction);
                assertThat(result).isNotNull();
            }
        }

        @Test
        @DisplayName("Should handle all days of week")
        void shouldHandleAllDaysOfWeek() {
            Instant monday = Instant.parse("2024-01-15T12:00:00Z");

            for (int day = 0; day < 7; day++) {
                Instant timestamp = monday.plus(day, ChronoUnit.DAYS);
                Transaction transaction = createTransactionAtTime(ACCOUNT_ID, timestamp);
                MLPrediction result = sageMakerMLAdapter.predict(transaction);
                assertThat(result).isNotNull();
            }
        }

        @Test
        @DisplayName("Should handle transaction with null location")
        void shouldHandleNullLocation() {
            Transaction transaction = createTransactionWithLocation(ACCOUNT_ID, null);

            // Should not throw exception
            assertThatCode(() -> sageMakerMLAdapter.predict(transaction))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should handle empty device ID")
        void shouldHandleEmptyDeviceId() {
            Transaction transaction = createTransactionWithDevice(ACCOUNT_ID, "");
            MLPrediction result = sageMakerMLAdapter.predict(transaction);
            assertThat(result).isNotNull();
        }
    }

    private Transaction createTransactionWithMerchant(String accountId, Merchant merchant) {
        return new Transaction(
                TransactionId.of(UUID.randomUUID()),
                accountId,
                amount(BigDecimal.valueOf(100.00)),
                TransactionType.PURCHASE,
                Channel.ONLINE,
                merchant,
                createLocation(),
                "DEVICE-123",
                Instant.now()
        );
    }

    private Transaction createTransactionWithType(String accountId, TransactionType type) {
        return new Transaction(
                TransactionId.of(UUID.randomUUID()),
                accountId,
                amount(BigDecimal.valueOf(100.00)),
                type,
                Channel.ONLINE,
                createMerchant(),
                createLocation(),
                "DEVICE-123",
                Instant.now()
        );
    }

    private Transaction createTransactionWithChannel(String accountId, Channel channel) {
        return new Transaction(
                TransactionId.of(UUID.randomUUID()),
                accountId,
                amount(BigDecimal.valueOf(100.00)),
                TransactionType.PURCHASE,
                channel,
                createMerchant(),
                createLocation(),
                "DEVICE-123",
                Instant.now()
        );
    }

    private Transaction createTransactionWithDevice(String accountId, String deviceId) {
        return new Transaction(
                TransactionId.of(UUID.randomUUID()),
                accountId,
                amount(BigDecimal.valueOf(100.00)),
                TransactionType.PURCHASE,
                Channel.ONLINE,
                createMerchant(),
                createLocation(),
                deviceId,
                Instant.now()
        );
    }

    private Transaction createTransactionWithLocation(String accountId, Location location) {
        return new Transaction(
                TransactionId.of(UUID.randomUUID()),
                accountId,
                amount(BigDecimal.valueOf(100.00)),
                TransactionType.PURCHASE,
                Channel.ONLINE,
                createMerchant(),
                location,
                "DEVICE-123",
                Instant.now()
        );
    }

    private Transaction createTransactionAtLocation(String accountId, Location location) {
        return createTransactionWithLocation(accountId, location);
    }

    private Transaction createTransactionWithSameMerchant(String accountId) {
        return createTransaction(accountId, BigDecimal.valueOf(50.00));
    }

    private Transaction createComplexFraudTransaction(String accountId) {
        // Night time + high amount + international + new merchant + no device
        return new Transaction(
                TransactionId.of(UUID.randomUUID()),
                accountId,
                amount(BigDecimal.valueOf(8000.00)),
                TransactionType.PURCHASE,
                Channel.ONLINE,
                new Merchant(MerchantId.of("NEW-MERCH"), "Unknown Merchant", MerchantCategory.ELECTRONICS),
                Location.of(51.5074, -0.1278, "GB", "London"),
                null,
                Instant.parse("2024-01-15T02:00:00Z")
        );
    }

    private Transaction createComplexLegitimateTransaction(String accountId) {
        // Day time + low amount + domestic + known merchant + has device
        return new Transaction(
                TransactionId.of(UUID.randomUUID()),
                accountId,
                amount(BigDecimal.valueOf(50.00)),
                TransactionType.PURCHASE,
                Channel.ONLINE,
                createMerchant(),
                createLocation(),
                "DEVICE-123",
                Instant.parse("2024-01-15T14:00:00Z")
        );
    }
    
    private void clearCache() {
        Cache cache = cacheManager.getCache("mlPredictions");
        if (cache != null) {
            cache.clear();
        }
    }

    private void setupDefaultMocks() {
        AccountProfile accountProfile = createAccountProfile(ACCOUNT_ID);
        when(accountServicePort.findAccountProfile(anyString())).thenReturn(accountProfile);
        when(transactionRepository.findByAccountIdAndTimestampBetween(anyString(), any(), any()))
                .thenReturn(List.of());
    }

    private void setupLocale() {
        originalLocale = Locale.getDefault();
        Locale.setDefault(new Locale.Builder()
                .setLanguage("af")
                .setRegion("ZA")
                .build());
    }

    private void restoreLocale() {
        Locale.setDefault(originalLocale);
    }

    private Transaction createTransaction(String accountId, BigDecimal amount) {
        return new Transaction(
                TransactionId.of(UUID.randomUUID()),
                accountId,
                amount(amount),
                TransactionType.PURCHASE,
                Channel.ONLINE,
                createMerchant(),
                createLocation(),
                "DEVICE-123",
                Instant.now()
        );
    }

    private Transaction createHighRiskTransaction(String accountId) {
        return new Transaction(
                TransactionId.of(UUID.randomUUID()),
                accountId,
                amount(BigDecimal.valueOf(10000.00)),
                TransactionType.ATM_WITHDRAWAL,
                Channel.ATM,
                createMerchant(),
                Location.of(-1.2921, 36.8219, "KE", "Nairobi"),
                null,
                Instant.now().minus(2, ChronoUnit.HOURS)
        );
    }

    private Transaction createForeignTransaction(String accountId) {
        return new Transaction(
                TransactionId.of(UUID.randomUUID()),
                accountId,
                amount(BigDecimal.valueOf(500.00)),
                TransactionType.PURCHASE,
                Channel.POS,
                createMerchant(),
                Location.of(51.5074, -0.1278, "GB", "London"),
                "DEVICE-123",
                Instant.now()
        );
    }

    private Transaction createTransactionAtTime(String accountId, Instant timestamp) {
        return new Transaction(
                TransactionId.of(UUID.randomUUID()),
                accountId,
                amount(BigDecimal.valueOf(200.00)),
                TransactionType.PURCHASE,
                Channel.ONLINE,
                createMerchant(),
                createLocation(),
                "DEVICE-123",
                timestamp
        );
    }

    private List<Transaction> createTransactionHistory(String accountId, int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> createTransaction(accountId, BigDecimal.valueOf(100.00 + i * 10)))
                .toList();
    }

    private AccountProfile createAccountProfile(String accountId) {
        return new AccountProfile(
                accountId,
                createLocation(),
                Instant.parse("2020-01-15T00:00:00Z")
        );
    }

    private Location createLocation() {
        return Location.of(-26.2041, 28.0473, "ZA", "Johannesburg");
    }

    private Merchant createMerchant() {
        return new Merchant(MerchantId.of("MERCH-123"), "Test Merchant", MerchantCategory.RETAIL);
    }

    private Money amount(BigDecimal value) {
        return new Money(value, Currency.getInstance("ZAR"));
    }
}