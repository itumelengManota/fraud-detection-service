package com.twenty9ine.frauddetection.infrastructure.adapter.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twenty9ine.frauddetection.domain.exception.AccountNotFoundException;
import com.twenty9ine.frauddetection.domain.exception.AccountServiceException;
import com.twenty9ine.frauddetection.domain.valueobject.AccountProfile;
import com.twenty9ine.frauddetection.infrastructure.adapter.account.config.AccountServiceTestConfig;
import com.twenty9ine.frauddetection.infrastructure.adapter.account.dto.AccountDto;
import com.twenty9ine.frauddetection.infrastructure.adapter.account.dto.LocationDto;
import com.twenty9ine.frauddetection.infrastructure.config.CacheConfig;
import org.jspecify.annotations.NonNull;
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

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = {
        AccountServiceRestAdapter.class,
        CacheConfig.class,
        AccountServiceTestConfig.class
    },
    properties = {
        "spring.flyway.enabled=false"
    }
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
    private static final String ACCOUNT_ID_SLOW = "ACC-SLOW";
    private static final String ACCOUNT_ID_RETRY = "ACC-RETRY";
    private static final String ACCOUNT_ID_BULKHEAD = "ACC-BULKHEAD";

    private static final String MOCKOON_IMAGE = "mockoon/cli:latest";

    @Container
    static GenericContainer<?> mockoon = new GenericContainer<>(DockerImageName.parse(MOCKOON_IMAGE))
            .withExposedPorts(3000)
            .withClasspathResourceMapping("mockoon/data-test.json", "/data/data.json", BindMode.READ_ONLY)
            .withCommand("--data", "/data/data.json")
            .withReuse(true);

    // Redis not needed - CacheConfig uses Caffeine (in-memory cache)
    // @Container
    // static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE))
    //         .withExposedPorts(6379)
    //         .withReuse(true);

    @Autowired
    private AccountServiceRestAdapter accountServiceRestAdapter;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        mockoon.start();

        // Account Service
        registry.add("account-service.base-url", AccountServiceRestAdapterIntegrationTest::buildAccountServiceUrl);
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
        void shouldRetrieveAccountProfileSuccessfully() throws Exception {

            AccountProfile result = accountServiceRestAdapter.findAccountProfile(ACCOUNT_ID_SUCCESS);

            assertThat(result).isNotNull();
            assertThat(result.accountId()).isEqualTo(ACCOUNT_ID_SUCCESS);
            assertThat(result.homeLocation()).isNotNull();
            assertThat(result.homeLocation().latitude()).isEqualTo(-26.2041);
            assertThat(result.homeLocation().longitude()).isEqualTo(28.0473);
            assertThat(result.homeLocation().country()).isEqualTo("ZA");
            assertThat(result.homeLocation().city()).isEqualTo("Johannesburg");
        }

//        @Test
//        @DisplayName("Should cache account profile after first retrieval")
//        void shouldCacheAccountProfile() throws Exception {
//            // When - First call
//            AccountProfile firstResult = accountServiceRestAdapter.findAccountProfile(ACCOUNT_ID_SUCCESS);
//
//            // Then - Verify cached
//            Cache cache = cacheManager.getCache("accountProfiles");
//            assertThat(cache).isNotNull();
//            AccountProfile cachedProfile = cache.get(ACCOUNT_ID_SUCCESS, AccountProfile.class);
//            assertThat(cachedProfile).isNotNull();
//            assertThat(cachedProfile.accountId()).isEqualTo(firstResult.accountId());
//
//            // When - Second call (should use cache)
//            mockServerClient.reset();
//            AccountProfile secondResult = accountServiceRestAdapter.findAccountProfile(ACCOUNT_ID_SUCCESS);
//
//            // Then - Same result from cache
//            assertThat(secondResult).isEqualTo(firstResult);
//        }
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

//        @Test
//        @DisplayName("Should retry on transient failures")
//        void shouldRetryOnTransientFailures() throws Exception {
//            // Given
//            String accountId = "ACC-RETRY-TEST";
//            AccountDto accountDto = createAccountDto(accountId);
//            AtomicInteger callCount = new AtomicInteger(0);
//
//            mockServerClient
//                    .when(HttpRequest.request()
//                            .withPath("/accounts/" + accountId + "/profiles"))
//                    .respond(request -> {
//                        int count = callCount.incrementAndGet();
//                        if (count < 2) {
//                            return HttpResponse.response().withStatusCode(500);
//                        }
//                        return HttpResponse.response()
//                                .withStatusCode(200)
//                                .withContentType(MediaType.APPLICATION_JSON)
//                                .withBody(objectMapper.writeValueAsString(accountDto));
//                    });
//
//            // When
//            AccountProfile result = accountServiceRestAdapter.findAccountProfile(accountId);
//
//            // Then
//            assertThat(result).isNotNull();
//            assertThat(callCount.get()).isGreaterThanOrEqualTo(2);
//        }

//        @Test
//        @DisplayName("Should use cached fallback when service fails")
//        void shouldUseCachedFallbackWhenServiceFails() throws Exception {
//            // Given
//            String accountId = "ACC-FALLBACK-TEST";
//            AccountDto accountDto = createAccountDto(accountId);
//
//            // First successful call to populate cache
//            mockSuccessfulResponse(accountId, accountDto);
//            AccountProfile cachedProfile = accountServiceRestAdapter.findAccountProfile(accountId);
//            assertThat(cachedProfile).isNotNull();
//
//            // Now make service fail
//            mockServerClient.reset();
//            mockServerClient
//                    .when(HttpRequest.request()
//                            .withPath("/accounts/" + accountId + "/profiles"))
//                    .respond(HttpResponse.response()
//                            .withStatusCode(500)
//                            .withDelay(TimeUnit.MILLISECONDS, 600)); // Exceed timeout
//
//            // When - Service fails but fallback retrieves from cache
//            AccountProfile fallbackResult = accountServiceRestAdapter.findAccountProfile(accountId);
//
//            // Then
//            assertThat(fallbackResult).isNotNull();
//            assertThat(fallbackResult.accountId()).isEqualTo(accountId);
//        }

//        @Test
//        @DisplayName("Should return null from fallback when cache is empty")
//        void shouldReturnNullFromFallbackWhenCacheEmpty() {
//            // Given
//            String accountId = "ACC-NO-CACHE";
//            mockServerClient
//                    .when(HttpRequest.request()
//                            .withPath("/accounts/" + accountId + "/profiles"))
//                    .respond(HttpResponse.response()
//                            .withStatusCode(500)
//                            .withDelay(TimeUnit.MILLISECONDS, 600));
//
//            // When
//            AccountProfile result = accountServiceRestAdapter.findAccountProfile(accountId);
//
//            // Then
//            assertThat(result).isNull();
//        }

        @Disabled
        @Test
        @DisplayName("Should respect timeout configuration")
        void shouldRespectTimeoutConfiguration() {
            assertThatThrownBy(() -> accountServiceRestAdapter.findAccountProfile(ACCOUNT_ID_TIMEOUT))
                    .isInstanceOf(AccountServiceException.class);
        }

//        @Test
//        @DisplayName("Should handle bulkhead limit")
//        void shouldHandleBulkheadLimit() throws Exception {
//            // Given
//            String accountId = "ACC-BULKHEAD-TEST";
//            AccountDto accountDto = createAccountDto(accountId);
//            int concurrentCalls = 30; // Exceeds bulkhead max of 25
//            CountDownLatch latch = new CountDownLatch(concurrentCalls);
//
//            mockServerClient
//                    .when(HttpRequest.request()
//                            .withPath("/accounts/" + accountId + "/profiles"))
//                    .respond(HttpResponse.response()
//                            .withStatusCode(200)
//                            .withContentType(MediaType.APPLICATION_JSON)
//                            .withBody(objectMapper.writeValueAsString(accountDto))
//                            .withDelay(TimeUnit.MILLISECONDS, 100));
//
//            // When
//            for (int i = 0; i < concurrentCalls; i++) {
//                new Thread(() -> {
//                    try {
//                        accountServiceRestAdapter.findAccountProfile(accountId);
//                    } catch (Exception e) {
//                        // Some calls may fail due to bulkhead
//                    } finally {
//                        latch.countDown();
//                    }
//                }).start();
//            }
//
//            // Then
//            boolean completed = latch.await(10, TimeUnit.SECONDS);
//            assertThat(completed).isTrue();
//        }

        @Disabled
        @Test
        @DisplayName("Should open circuit breaker after multiple failures")
        void shouldOpenCircuitBreakerAfterFailures() throws Exception {
            // Trigger multiple failures
            for (int i = 0; i < 15; i++) {
                try {
                    accountServiceRestAdapter.findAccountProfile(ACCOUNT_ID_CIRCUIT_BREAKER);
                } catch (Exception ignored) {}
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

    private AccountDto createAccountDto(String accountId) {
        return new AccountDto(
                accountId,
                new LocationDto(-26.2041, 28.0473, "ZA", "Johannesburg"),
                Instant.parse("2020-01-15T00:00:00Z")
        );
    }
}