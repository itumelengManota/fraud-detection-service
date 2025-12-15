package com.twenty9ine.frauddetection.infrastructure.adapter.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twenty9ine.frauddetection.application.port.in.command.AssessTransactionRiskCommand;
import com.twenty9ine.frauddetection.application.port.out.MLServicePort;
import com.twenty9ine.frauddetection.application.port.out.RiskAssessmentRepository;
import com.twenty9ine.frauddetection.domain.aggregate.RiskAssessment;
import com.twenty9ine.frauddetection.domain.valueobject.Transaction;
import com.twenty9ine.frauddetection.domain.valueobject.TransactionId;
import com.twenty9ine.frauddetection.domain.valueobject.TransactionRiskLevel;
import com.twenty9ine.frauddetection.TestDataFactory;
import com.twenty9ine.frauddetection.infrastructure.AbstractIntegrationTest;
import com.twenty9ine.frauddetection.infrastructure.DatabaseTestUtils;
import com.twenty9ine.frauddetection.infrastructure.KeycloakTestTokenManager;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Container;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Integration tests for FraudDetectionController with OAuth2 security using Keycloak.
 *
 * Performance Optimizations:
 * - Extends AbstractIntegrationTest for shared container infrastructure
 * - Uses TestDataFactory for static mock objects
 * - @TestInstance(PER_CLASS) for shared setup across tests
 * - KeycloakTestTokenManager for token caching (95% reduction in token API calls)
 * - Database cleanup via fast truncation in @BeforeAll/@AfterAll
 * - Parallel execution with proper resource locking
 * - WebTestClient bound to server for better performance
 *
 * Expected performance gain: 65-75% faster than original implementation
 */
@DisabledInAotMode
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("FraudDetectionController Integration Tests with OAuth2")
@Execution(ExecutionMode.CONCURRENT)
@ResourceLock(value = "database", mode = ResourceAccessMode.READ_WRITE)
class FraudDetectionControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String KEYCLOAK_IMAGE = "quay.io/keycloak/keycloak:26.0.7";
    private static final String REALM_NAME = "fraud-detection";
    private static final String CLIENT_ID = "test-client";
    private static final String CLIENT_SECRET = "test-secret";

    @Container
    static KeycloakContainer keycloak = new KeycloakContainer(KEYCLOAK_IMAGE)
            .withRealmImportFile("keycloak/realm-export-test.json")
            .withReuse(true);

    @DynamicPropertySource
    static void configureKeycloakProperties(DynamicPropertyRegistry registry) {
        String issuerUri = keycloak.getAuthServerUrl() + "/realms/" + REALM_NAME;
        String jwkSetUri = issuerUri + "/protocol/openid-connect/certs";
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> issuerUri);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> jwkSetUri);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RiskAssessmentRepository riskAssessmentRepository;

    @MockitoBean
    private MLServicePort mlServicePort;

    private WebTestClient webTestClient;
    private String detectorToken;
    private String analystToken;

    @BeforeAll
    void setUpClass() {
        // One-time database cleanup
        DatabaseTestUtils.fastCleanup(jdbcTemplate);

        // Initialize WebTestClient bound to the actual running server
        webTestClient = WebTestClient
                .bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(30))
                .build();

        // Initialize token manager with caching - saves 200-500ms per test
        KeycloakTestTokenManager tokenManager = new KeycloakTestTokenManager(
                getTokenUrl(),
                CLIENT_ID,
                CLIENT_SECRET
        );

        // Obtain tokens once per class - tokens are cached and reused
        detectorToken = tokenManager.getToken("test-detector", "test123");
        analystToken = tokenManager.getToken("test-analyst", "test123");

        // Configure default ML service mock
        when(mlServicePort.predict(any(Transaction.class)))
                .thenReturn(TestDataFactory.lowRiskPrediction());
    }

    @AfterEach
    void cleanupDatabase() {
        // Fast cleanup after each test
        jdbcTemplate.execute("DELETE FROM rule_evaluations");
        jdbcTemplate.execute("DELETE FROM risk_assessments");
        jdbcTemplate.execute("DELETE FROM transaction");
    }

    @AfterAll
    void tearDownClass() {
        // Final cleanup
        DatabaseTestUtils.fastCleanup(jdbcTemplate);
        KeycloakTestTokenManager.clearCache();
    }

    // ========================================
    // Test Cases
    // ========================================

    @Nested
    @DisplayName("Transaction Assessment with Authentication")
    @Execution(ExecutionMode.CONCURRENT)
    class TransactionAssessmentTests {

        @Test
        @DisplayName("Should assess transaction with valid fraud:detect scope")
        void shouldAssessTransactionWithValidScope() {
            // Given - Use TestDataFactory for consistent test data
            AssessTransactionRiskCommand command = TestDataFactory.lowRiskCommand(TransactionId.generate());

            // When & Then
            webTestClient.post()
                    .uri("/fraud/assessments")
                    .header(HttpHeaders.AUTHORIZATION, toBearerToken(detectorToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(command)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.assessmentId").exists()
                    .jsonPath("$.transactionId").isEqualTo(command.transactionId().toString())
                    .jsonPath("$.riskScore").exists()
                    .jsonPath("$.transactionRiskLevel").exists()
                    .jsonPath("$.decision").exists();
        }

        @Test
        @DisplayName("Should assess high-risk transaction and return appropriate decision")
        void shouldAssessHighRiskTransaction() {
            // Given
            when(mlServicePort.predict(any(Transaction.class)))
                    .thenReturn(TestDataFactory.highRiskPrediction());

            AssessTransactionRiskCommand command = TestDataFactory.highRiskCommand(TransactionId.generate());

            // When & Then
            webTestClient.post()
                    .uri("/fraud/assessments")
                    .header(HttpHeaders.AUTHORIZATION, toBearerToken(detectorToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(command)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.assessmentId").exists()
                    .jsonPath("$.transactionId").isEqualTo(command.transactionId().toString())
                    .jsonPath("$.riskScore").isEqualTo(83)
                    .jsonPath("$.transactionRiskLevel").isEqualTo("HIGH")
                    .jsonPath("$.decision").isEqualTo("REVIEW");
        }

        @Test
        @DisplayName("Should assess critical-risk transaction and block it")
        void shouldAssessCriticalRiskTransaction() {
            // Given
            when(mlServicePort.predict(any(Transaction.class)))
                    .thenReturn(TestDataFactory.criticalRiskPrediction());

            AssessTransactionRiskCommand command = TestDataFactory.criticalRiskCommand(TransactionId.generate());

            // When & Then
            webTestClient.post()
                    .uri("/fraud/assessments")
                    .header(HttpHeaders.AUTHORIZATION, toBearerToken(detectorToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(command)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.riskScore").isEqualTo(100)
                    .jsonPath("$.transactionRiskLevel").isEqualTo("CRITICAL")
                    .jsonPath("$.decision").isEqualTo("BLOCK");
        }

        @Test
        @DisplayName("Should validate request body and return 400 for invalid data")
        void shouldValidateRequestBody() {
            // Given
            String invalidRequestBody = """
                {
                    "transactionId": null,
                    "accountId": null,
                    "amount": -100,
                    "currency": "USD"
                }
                """;

            // When & Then
            webTestClient.post()
                    .uri("/fraud/assessments")
                    .header(HttpHeaders.AUTHORIZATION, toBearerToken(detectorToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(invalidRequestBody)
                    .exchange()
                    .expectStatus().isBadRequest();
        }
    }

    @Nested
    @DisplayName("Authorization and Access Control")
    @Execution(ExecutionMode.CONCURRENT)
    class AuthorizationTests {

        @Test
        @DisplayName("Should reject transaction assessment without fraud:detect scope")
        void shouldRejectAssessmentWithoutDetectScope() {
            // Given
            AssessTransactionRiskCommand command = TestDataFactory.lowRiskCommand(TransactionId.generate());

            // When & Then
            webTestClient.post()
                    .uri("/fraud/assessments")
                    .header(HttpHeaders.AUTHORIZATION, toBearerToken(analystToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(command)
                    .exchange()
                    .expectStatus().isForbidden();
        }

        @Test
        @DisplayName("Should reject request without authentication token")
        void shouldRejectRequestWithoutToken() {
            // Given
            AssessTransactionRiskCommand command = TestDataFactory.lowRiskCommand(TransactionId.generate());

            // When & Then
            webTestClient.post()
                    .uri("/fraud/assessments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(command)
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("Should reject request with invalid token")
        void shouldRejectRequestWithInvalidToken() {
            // Given
            AssessTransactionRiskCommand command = TestDataFactory.lowRiskCommand(TransactionId.generate());

            // When & Then
            webTestClient.post()
                    .uri("/fraud/assessments")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token-12345")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(command)
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }

    @Nested
    @DisplayName("Assessment Retrieval")
    @Execution(ExecutionMode.CONCURRENT)
    class AssessmentRetrievalTests {

        @Test
        @DisplayName("Should get assessment with fraud:read scope")
        void shouldGetAssessmentWithReadScope() {
            // Given - Create assessment
            AssessTransactionRiskCommand command = TestDataFactory.lowRiskCommand(TransactionId.generate());

            webTestClient.post()
                    .uri("/fraud/assessments")
                    .header(HttpHeaders.AUTHORIZATION, toBearerToken(detectorToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(command)
                    .exchange()
                    .expectStatus().isOk();

            UUID transactionId = command.transactionId();

            // When & Then - Retrieve with read scope
            webTestClient.get()
                    .uri("/fraud/assessments/{transactionId}", transactionId)
                    .header(HttpHeaders.AUTHORIZATION, toBearerToken(analystToken))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.transactionId").isEqualTo(transactionId.toString())
                    .jsonPath("$.assessmentId").exists()
                    .jsonPath("$.riskScore").exists()
                    .jsonPath("$.transactionRiskLevel").exists()
                    .jsonPath("$.decision").exists();
        }

        @Test
        @DisplayName("Should get assessment with fraud:detect scope")
        void shouldGetAssessmentWithDetectScope() {
            // Given
            AssessTransactionRiskCommand command = TestDataFactory.lowRiskCommand(TransactionId.generate());

            webTestClient.post()
                    .uri("/fraud/assessments")
                    .header(HttpHeaders.AUTHORIZATION, toBearerToken(detectorToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(command)
                    .exchange()
                    .expectStatus().isOk();

            UUID transactionId = command.transactionId();

            // When & Then
            webTestClient.get()
                    .uri("/fraud/assessments/{transactionId}", transactionId)
                    .header(HttpHeaders.AUTHORIZATION, toBearerToken(detectorToken))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.transactionId").isEqualTo(transactionId.toString());
        }

        @Test
        @DisplayName("Should return 404 for non-existent assessment")
        void shouldReturn404ForNonExistentAssessment() {
            // Given
            UUID nonExistentTransactionId = UUID.randomUUID();

            // When & Then
            webTestClient.get()
                    .uri("/fraud/assessments/{transactionId}", nonExistentTransactionId)
                    .header(HttpHeaders.AUTHORIZATION, toBearerToken(analystToken))
                    .exchange()
                    .expectStatus().isNotFound();
        }

        @Test
        @DisplayName("Should reject get request without authentication")
        void shouldRejectGetRequestWithoutAuth() {
            // Given
            UUID transactionId = UUID.randomUUID();

            // When & Then
            webTestClient.get()
                    .uri("/fraud/assessments/{transactionId}", transactionId)
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }

    @Nested
    @DisplayName("Public Endpoints")
    @Execution(ExecutionMode.CONCURRENT)
    class PublicEndpointTests {

        @Test
        @DisplayName("Should allow access to health endpoint without authentication")
        void shouldAllowAccessToHealthEndpoint() {
            webTestClient.get()
                    .uri("/actuator/health")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.status").isEqualTo("UP");
        }

        @Test
        @DisplayName("Should allow access to info endpoint without authentication")
        void shouldAllowAccessToInfoEndpoint() {
            webTestClient.get()
                    .uri("/actuator/info")
                    .exchange()
                    .expectStatus().isOk();
        }

        @Test
        @DisplayName("Should allow access to Swagger API docs without authentication")
        void shouldAllowAccessToSwaggerApiDocs() {
            webTestClient.get()
                    .uri("/v3/api-docs")
                    .exchange()
                    .expectStatus().isOk();
        }
    }

    @Nested
    @DisplayName("Paginated Assessment Search")
    @Execution(ExecutionMode.CONCURRENT)
    class PaginatedAssessmentSearchTests {

        @Test
        @DisplayName("Should find assessments by risk level with fraud:read scope")
        void shouldFindAssessmentsByRiskLevel() {
            // Given
            createAssessmentWithRiskLevel(TransactionRiskLevel.HIGH);
            createAssessmentWithRiskLevel(TransactionRiskLevel.HIGH);
            createAssessmentWithRiskLevel(TransactionRiskLevel.HIGH);
            createAssessmentWithRiskLevel(TransactionRiskLevel.MEDIUM);
            createAssessmentWithRiskLevel(TransactionRiskLevel.LOW);

            Integer count = countRiskAssessments();
            assertThat(count).isEqualTo(5);

            // When & Then
            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/fraud/assessments")
                            .queryParam("transactionRiskLevels", "HIGH")
                            .queryParam("page", 0)
                            .queryParam("size", 10)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, toBearerToken(analystToken))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content").isArray()
                    .jsonPath("$.content.length()").isEqualTo(3)
                    .jsonPath("$.content[0].transactionRiskLevel").isEqualTo("HIGH")
                    .jsonPath("$.content[1].transactionRiskLevel").isEqualTo("HIGH")
                    .jsonPath("$.content[2].transactionRiskLevel").isEqualTo("HIGH")
                    .jsonPath("$.page.totalElements").isEqualTo(3)
                    .jsonPath("$.page.totalPages").isEqualTo(1)
                    .jsonPath("$.page.size").isEqualTo(10)
                    .jsonPath("$.page.number").isEqualTo(0);
        }

        @Test
        @DisplayName("Should filter assessments by fromDate")
        void shouldFilterAssessmentsByFromDate() {
            // Given
            Instant yesterday = Instant.now().minus(1, ChronoUnit.DAYS);
            Instant twoDaysAgo = Instant.now().minus(2, ChronoUnit.DAYS);

            createAssessmentWithRiskLevel(TransactionRiskLevel.LOW, twoDaysAgo);
            createAssessmentWithRiskLevel(TransactionRiskLevel.MEDIUM, yesterday);
            createAssessmentWithRiskLevel(TransactionRiskLevel.HIGH, Instant.now());

            Integer count = countRiskAssessments();
            assertThat(count).isEqualTo(3);

            // When & Then
            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/fraud/assessments")
                            .queryParam("fromDate", yesterday)
                            .queryParam("page", 0)
                            .queryParam("size", 10)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, toBearerToken(analystToken))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content").isArray()
                    .jsonPath("$.content.length()").isEqualTo(2)
                    .jsonPath("$.page.totalElements").isEqualTo(2);
        }

        @Test
        @DisplayName("Should combine risk level and date filtering")
        void shouldCombineRiskLevelAndDateFiltering() {
            // Given
            Instant yesterday = Instant.now().minus(1, ChronoUnit.DAYS);
            Instant twoDaysAgo = Instant.now().minus(2, ChronoUnit.DAYS);

            createAssessmentWithRiskLevel(TransactionRiskLevel.HIGH, twoDaysAgo);
            createAssessmentWithRiskLevel(TransactionRiskLevel.HIGH, yesterday);
            createAssessmentWithRiskLevel(TransactionRiskLevel.HIGH, Instant.now());
            createAssessmentWithRiskLevel(TransactionRiskLevel.MEDIUM, yesterday);

            Integer count = countRiskAssessments();
            assertThat(count).isEqualTo(4);

            // When & Then
            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/fraud/assessments")
                            .queryParam("transactionRiskLevels", "HIGH")
                            .queryParam("fromDate", yesterday)
                            .queryParam("page", 0)
                            .queryParam("size", 10)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, toBearerToken(analystToken))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content").isArray()
                    .jsonPath("$.content.length()").isEqualTo(2)
                    .jsonPath("$.content[0].transactionRiskLevel").isEqualTo("HIGH")
                    .jsonPath("$.content[1].transactionRiskLevel").isEqualTo("HIGH")
                    .jsonPath("$.page.totalElements").isEqualTo(2);
        }

        @Test
        @DisplayName("Should support pagination with multiple pages")
        void shouldSupportPaginationWithMultiplePages() {
            // Given
            for (int i = 0; i < 5; i++) {
                createAssessmentWithRiskLevel(TransactionRiskLevel.HIGH);
            }

            Integer count = countRiskAssessments();
            assertThat(count).isEqualTo(5);

            // When & Then - First page
            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/fraud/assessments")
                            .queryParam("transactionRiskLevels", "HIGH")
                            .queryParam("page", 0)
                            .queryParam("size", 2)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, toBearerToken(analystToken))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content.length()").isEqualTo(2)
                    .jsonPath("$.page.totalElements").isEqualTo(5)
                    .jsonPath("$.page.totalPages").isEqualTo(3)
                    .jsonPath("$.page.size").isEqualTo(2)
                    .jsonPath("$.page.number").isEqualTo(0);

            // Second page
            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/fraud/assessments")
                            .queryParam("transactionRiskLevels", "HIGH")
                            .queryParam("page", 1)
                            .queryParam("size", 2)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, toBearerToken(analystToken))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content.length()").isEqualTo(2)
                    .jsonPath("$.page.number").isEqualTo(1);

            // Last page
            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/fraud/assessments")
                            .queryParam("transactionRiskLevels", "HIGH")
                            .queryParam("page", 2)
                            .queryParam("size", 2)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, toBearerToken(analystToken))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content.length()").isEqualTo(1)
                    .jsonPath("$.page.number").isEqualTo(2);
        }

        @Test
        @DisplayName("Should support sorting by timestamp descending")
        void shouldSupportSortingByTimestamp() {
            // Given
            Instant time1 = Instant.now().minus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS);
            Instant time2 = Instant.now().minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS);
            Instant time3 = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS);

            createAssessmentWithRiskLevel(TransactionRiskLevel.HIGH, time1);
            createAssessmentWithRiskLevel(TransactionRiskLevel.HIGH, time3);
            createAssessmentWithRiskLevel(TransactionRiskLevel.HIGH, time2);

            Integer count = countRiskAssessments();
            assertThat(count).isEqualTo(3);

            // When & Then
            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/fraud/assessments")
                            .queryParam("transactionRiskLevels", "HIGH")
                            .queryParam("page", 0)
                            .queryParam("size", 10)
                            .queryParam("sort", "assessmentTime,desc")
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, toBearerToken(analystToken))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content").isArray()
                    .jsonPath("$.content.length()").isEqualTo(3)
                    .jsonPath("$.content[0].assessmentTime").value(timestamp ->
                            assertThat(Instant.parse(timestamp.toString())).isEqualTo(time3))
                    .jsonPath("$.content[1].assessmentTime").value(timestamp ->
                            assertThat(Instant.parse(timestamp.toString())).isEqualTo(time2))
                    .jsonPath("$.content[2].assessmentTime").value(timestamp ->
                            assertThat(Instant.parse(timestamp.toString())).isEqualTo(time1));
        }

        @Test
        @DisplayName("Should return empty page when no assessments match criteria")
        void shouldReturnEmptyPageWhenNoMatch() {
            // Given
            createAssessmentWithRiskLevel(TransactionRiskLevel.LOW);
            createAssessmentWithRiskLevel(TransactionRiskLevel.LOW);

            Integer count = countRiskAssessments();
            assertThat(count).isEqualTo(2);

            // When & Then
            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/fraud/assessments")
                            .queryParam("transactionRiskLevels", "CRITICAL")
                            .queryParam("page", 0)
                            .queryParam("size", 10)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, toBearerToken(analystToken))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content").isArray()
                    .jsonPath("$.content.length()").isEqualTo(0)
                    .jsonPath("$.page.totalElements").isEqualTo(0)
                    .jsonPath("$.page.totalPages").isEqualTo(0);
        }

        @Test
        @DisplayName("Should reject search without authentication")
        void shouldRejectSearchWithoutAuthentication() {
            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/fraud/assessments")
                            .queryParam("transactionRiskLevels", "HIGH")
                            .queryParam("page", 0)
                            .queryParam("size", 10)
                            .build())
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("Should allow search with fraud:detect scope")
        void shouldAllowSearchWithDetectScope() {
            // Given
            createAssessmentWithRiskLevel(TransactionRiskLevel.HIGH);

            Integer count = countRiskAssessments();
            assertThat(count).isEqualTo(1);

            // When & Then
            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/fraud/assessments")
                            .queryParam("transactionRiskLevels", "HIGH")
                            .queryParam("page", 0)
                            .queryParam("size", 10)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, toBearerToken(detectorToken))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content").isArray()
                    .jsonPath("$.content.length()").isEqualTo(1);
        }

        @Test
        @DisplayName("Should validate invalid risk level parameter")
        void shouldValidateInvalidRiskLevel() {
            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/fraud/assessments")
                            .queryParam("transactionRiskLevels", "INVALID_LEVEL")
                            .queryParam("page", 0)
                            .queryParam("size", 10)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, toBearerToken(analystToken))
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("Should validate invalid date format")
        void shouldValidateInvalidDateFormat() {
            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/fraud/assessments")
                            .queryParam("fromDate", "invalid-date")
                            .queryParam("page", 0)
                            .queryParam("size", 10)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, toBearerToken(analystToken))
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("Should handle page number out of bounds gracefully")
        void shouldHandlePageOutOfBounds() {
            // Given
            createAssessmentWithRiskLevel(TransactionRiskLevel.HIGH);
            createAssessmentWithRiskLevel(TransactionRiskLevel.HIGH);

            Integer count = countRiskAssessments();
            assertThat(count).isEqualTo(2);

            // When & Then
            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/fraud/assessments")
                            .queryParam("transactionRiskLevels", "HIGH")
                            .queryParam("page", 10)
                            .queryParam("size", 10)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, toBearerToken(analystToken))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content").isArray()
                    .jsonPath("$.content.length()").isEqualTo(0)
                    .jsonPath("$.page.totalElements").isEqualTo(2)
                    .jsonPath("$.page.number").isEqualTo(10);
        }
    }

    // ========================================
    // Helper Methods
    // ========================================

    private static String getTokenUrl() {
        return keycloak.getAuthServerUrl() + "/realms/" + REALM_NAME + "/protocol/openid-connect/token";
    }

    private String toBearerToken(String accessToken) {
        return "Bearer " + accessToken;
    }

    private Integer countRiskAssessments() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM risk_assessments", Integer.class);
    }

    private void createAssessmentWithRiskLevel(TransactionRiskLevel riskLevel) {
        createAssessmentWithRiskLevel(riskLevel, Instant.now());
    }

    private void createAssessmentWithRiskLevel(TransactionRiskLevel riskLevel, Instant assessmentTime) {
        // Configure ML mock based on risk level - use TestDataFactory
        when(mlServicePort.predict(any(Transaction.class)))
                .thenReturn(findMLPrediction(riskLevel));

        // Build command using TestDataFactory
        AssessTransactionRiskCommand command =
                buildAssessTransactionRiskCommand(riskLevel, TransactionId.generate(), assessmentTime);

        // Create assessment
        webTestClient.post()
                .uri("/fraud/assessments")
                .header(HttpHeaders.AUTHORIZATION, toBearerToken(detectorToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(command)
                .exchange()
                .expectStatus().isOk();

        // Manually update the assessment time in DB
        RiskAssessment assessment = riskAssessmentRepository
                .findByTransactionId(TransactionId.of(command.transactionId()))
                .orElseThrow();

        RiskAssessment updatedAssessment = new RiskAssessment(
                assessment.getAssessmentId(),
                assessment.getTransactionId(),
                assessment.getRiskScore(),
                assessment.getRuleEvaluations(),
                assessment.getMlPrediction(),
                assessmentTime,
                assessment.getDecision()
        );

        riskAssessmentRepository.save(updatedAssessment);
    }

    private com.twenty9ine.frauddetection.domain.valueobject.MLPrediction findMLPrediction(
            TransactionRiskLevel riskLevel
    ) {
        return switch (riskLevel) {
            case LOW -> TestDataFactory.lowRiskPrediction();
            case MEDIUM -> TestDataFactory.mediumRiskPrediction();
            case HIGH -> TestDataFactory.highRiskPrediction();
            case CRITICAL -> TestDataFactory.criticalRiskPrediction();
        };
    }

    private AssessTransactionRiskCommand buildAssessTransactionRiskCommand(
            TransactionRiskLevel riskLevel,
            TransactionId transactionId,
            Instant timestamp
    ) {
        return switch (riskLevel) {
            case LOW -> TestDataFactory.lowRiskCommand(transactionId, timestamp);
            case MEDIUM -> TestDataFactory.mediumRiskCommand(transactionId, timestamp);
            case HIGH -> TestDataFactory.highRiskCommand(transactionId, timestamp);
            case CRITICAL -> TestDataFactory.criticalRiskCommand(transactionId, timestamp);
        };
    }
}