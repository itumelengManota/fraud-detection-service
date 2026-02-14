package com.twenty9ine.frauddetection.infrastructure.adapter.account;

import com.twenty9ine.frauddetection.domain.exception.AccountNotFoundException;
import com.twenty9ine.frauddetection.domain.exception.AccountServiceException;
import com.twenty9ine.frauddetection.domain.valueobject.AccountProfile;
import com.twenty9ine.frauddetection.infrastructure.adapter.account.config.AccountServiceTestConfig;
import com.twenty9ine.frauddetection.infrastructure.config.RedisConfig;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = {AccountServiceRestAdapter.class, RedisConfig.class, AccountServiceTestConfig.class}
)
@ActiveProfiles("test")
@DisabledInAotMode
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("AccountServiceRestAdapter Integration Tests")
class AccountServiceRestAdapterIntegrationTest {

    private static final String ACCOUNT_ID_SUCCESS = "ACC-12345-1";
    private static final String ACCOUNT_ID_NOT_FOUND = "ACC-12345-3";
    private static final String ACCOUNT_ID_SERVER_ERROR = "ACC-12345-4";

    private static final String ACCOUNT_ID_TIMEOUT = "ACC-TIMEOUT";
    private static final String ACCOUNT_ID_CIRCUIT_BREAKER = "ACC-CIRCUIT-BREAKER";
    private static final String MOCKOON_IMAGE = "mockoon/cli:latest";

    @Container
    static GenericContainer<?> mockoon = new GenericContainer<>(DockerImageName.parse(MOCKOON_IMAGE))
            .withExposedPorts(3000)
            .withClasspathResourceMapping("mockoon/data-test.json", "/data/data.json", BindMode.READ_ONLY)
            .withCommand("--data", "/data/data.json")
            .withReuse(true);

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .withReuse(true);

    @Autowired
    private AccountServiceRestAdapter accountServiceRestAdapter;

    @Autowired
    private CacheManager cacheManager;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        mockoon.start();
        redis.start();

        // Account Service
        registry.add("account-service.base-url", AccountServiceRestAdapterIntegrationTest::buildAccountServiceUrl);

        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);

        registry.add("spring.flyway.enabled", () -> false);
    }

    private static String buildAccountServiceUrl() {
        return "http://%s:%s".formatted(mockoon.getHost(), mockoon.getFirstMappedPort());
    }

    @BeforeEach
    void setUp() {
        clearCache();
    }

    @AfterEach
    void tearDown() {
        clearCache();
    }

    @Nested
    @DisplayName("Successful Account Retrieval")
    class SuccessfulRetrieval {

        @Test
        @DisplayName("Should retrieve account profile successfully")
        void shouldRetrieveAccountProfileSuccessfully() {

            AccountProfile result = accountServiceRestAdapter.findAccountProfile(ACCOUNT_ID_SUCCESS);

            assertThat(result).isNotNull();
            assertThat(result.accountId()).isEqualTo(ACCOUNT_ID_SUCCESS);
            assertThat(result.homeLocation()).isNotNull();
            assertThat(result.homeLocation().latitude()).isEqualTo(-26.2041);
            assertThat(result.homeLocation().longitude()).isEqualTo(28.0473);
            assertThat(result.homeLocation().country()).isEqualTo("ZA");
            assertThat(result.homeLocation().city()).isEqualTo("Johannesburg");
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        @DisplayName("Should throw AccountNotFoundException for 404 response")
        void shouldThrowAccountNotFoundExceptionFor404() {
            assertThatThrownBy(() -> accountServiceRestAdapter.findAccountProfile(ACCOUNT_ID_NOT_FOUND))
                    .isInstanceOf(AccountNotFoundException.class)
                    .hasMessageContaining("Account profile not found");
        }

        @Test
        @DisplayName("Should throw AccountServiceException for 5xx response")
        void shouldThrowAccountServiceExceptionFor5xx() {
            assertThatThrownBy(() -> accountServiceRestAdapter.findAccountProfile(ACCOUNT_ID_SERVER_ERROR))
                    .isInstanceOf(AccountServiceException.class)
                    .hasMessageContaining("Account service unavailable");
        }
    }

    @Nested
    @DisplayName("Resilience Features")
    class ResilienceFeatures {

        @Disabled("Requires specific timeout configuration in mockoon to trigger")
        @Test
        @DisplayName("Should respect timeout configuration")
        void shouldRespectTimeoutConfiguration() {
            assertThatThrownBy(() -> accountServiceRestAdapter.findAccountProfile(ACCOUNT_ID_TIMEOUT))
                    .isInstanceOf(AccountServiceException.class);
        }

        @Disabled("Requires specific failure configuration in mockoon to trigger circuit breaker")
        @Test
        @DisplayName("Should open circuit breaker after multiple failures")
        void shouldOpenCircuitBreakerAfterFailures() {
            // Trigger multiple failures
            for (int i = 0; i < 15; i++) {
                accountServiceRestAdapter.findAccountProfile(ACCOUNT_ID_CIRCUIT_BREAKER);
            }

            // Circuit should be open, fallback should be triggered
            AccountProfile result = accountServiceRestAdapter.findAccountProfile(ACCOUNT_ID_CIRCUIT_BREAKER);
            assertThat(result).isNull(); // Or check cached value// Fast fail without actual call
        }
    }

    // Helper Methods

    private void clearCache() {
        Cache cache = cacheManager.getCache("accountProfiles");
        if (cache != null) {
            cache.clear();
        }
    }
}