package com.twenty9ine.frauddetection.infrastructure.adapter.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twenty9ine.frauddetection.application.port.in.command.AssessTransactionRiskCommand;
import com.twenty9ine.frauddetection.application.port.out.MLServicePort;
import com.twenty9ine.frauddetection.application.port.out.RiskAssessmentRepository;
import com.twenty9ine.frauddetection.application.service.FraudDetectionApplicationServiceIntegrationTest;
import com.twenty9ine.frauddetection.domain.aggregate.RiskAssessment;
import com.twenty9ine.frauddetection.domain.valueobject.MLPrediction;
import com.twenty9ine.frauddetection.domain.valueobject.Transaction;
import com.twenty9ine.frauddetection.domain.valueobject.TransactionId;
import com.twenty9ine.frauddetection.domain.valueobject.TransactionRiskLevel;
import com.twenty9ine.frauddetection.infrastructure.adapter.persistence.entity.RiskAssessmentEntity;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static com.twenty9ine.frauddetection.application.service.FraudDetectionApplicationServiceIntegrationTest.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Integration test for FraudDetectionController with OAuth2 security using Keycloak.
 * Uses Testcontainers for complete infrastructure setup.
 * Uses WebTestClient for API testing and RestClient for token acquisition.
 */
@DisabledInAotMode
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@DisplayName("FraudDetectionController Integration Tests with OAuth2")
@Execution(ExecutionMode.SAME_THREAD)
class FraudDetectionControllerIntegrationTest {

    private static final String KEYCLOAK_IMAGE = "quay.io/keycloak/keycloak:26.0.7";
    private static final String POSTGRES_IMAGE = "postgres:17-alpine";
    private static final String REDIS_IMAGE = "redis:7-alpine";
    private static final String KAFKA_IMAGE = "apache/kafka:latest";
    private static final String APICURIO_IMAGE = "apicurio/apicurio-registry-mem:2.6.13.Final";

    private static final String REALM_NAME = "fraud-detection";
    private static final String CLIENT_ID = "test-client";
    private static final String CLIENT_SECRET = "test-secret";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse(POSTGRES_IMAGE))
            .withDatabaseName("frauddetection_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE))
            .withExposedPorts(6379)
            .withReuse(true);

    @Container
    static org.testcontainers.kafka.KafkaContainer kafka = new org.testcontainers.kafka.KafkaContainer(
            DockerImageName.parse(KAFKA_IMAGE))
            .withReuse(true);

    @Container
    static GenericContainer<?> apicurioRegistry = new GenericContainer<>(DockerImageName.parse(APICURIO_IMAGE))
            .withExposedPorts(8080)
            .dependsOn(kafka)
            .withReuse(true);

    @Container
    static KeycloakContainer keycloak = new KeycloakContainer(KEYCLOAK_IMAGE)
            .withRealmImportFile("keycloak/realm-export-test.json")
            .withReuse(true);
    @Autowired
    private RiskAssessmentRepository riskAssessmentRepository;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Database
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        // Redis
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);

        // Kafka
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);

        // Apicurio Registry
        String apicurioUrl = getApicurioUrl();
        registry.add("apicurio.registry.url", () -> apicurioUrl);
        registry.add("spring.kafka.consumer.properties.apicurio.registry.url", () -> apicurioUrl);
        registry.add("spring.kafka.producer.properties.apicurio.registry.url", () -> apicurioUrl);

        // Keycloak OAuth2
        String issuerUri = keycloak.getAuthServerUrl() + "/realms/" + REALM_NAME;
        String jwkSetUri = issuerUri + "/protocol/openid-connect/certs";
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> issuerUri);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> jwkSetUri);

        // Disable AWS SageMaker
        registry.add("aws.sagemaker.enabled", () -> "false");
    }

    private static String getApicurioUrl() {
        return "http://" + apicurioRegistry.getHost() + ":" + apicurioRegistry.getFirstMappedPort() + "/apis/registry/v2";
    }

    private static String getTokenUrl() {
        return keycloak.getAuthServerUrl() + "/realms/" + REALM_NAME + "/protocol/openid-connect/token";
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private MLServicePort mlServicePort;

    private WebTestClient webTestClient;
    private RestClient restClient;
    private String detectorToken;
    private String analystToken;

    @BeforeEach
    void setUp() {
        // Initialize WebTestClient bound to the actual running server
        webTestClient = WebTestClient
                .bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(30))
                .build();

        // Initialize RestClient for token acquisition
        restClient = RestClient.create();

        // Obtain tokens for test users before each test
        detectorToken = obtainAccessToken("test-detector", "test123");
        analystToken = obtainAccessToken("test-analyst", "test123");

        // Configure ML service mock with default behavior
        when(mlServicePort.predict(any(Transaction.class)))
                .thenReturn(mockLowRiskPrediction());
    }

    @AfterEach
    void cleanupDatabase() {
        jdbcTemplate.execute("DELETE FROM rule_evaluations");
        jdbcTemplate.execute("DELETE FROM risk_assessments");
        jdbcTemplate.execute("DELETE FROM transaction");
    }

    @Nested
    @DisplayName("Transaction Assessment with Authentication")
    class TransactionAssessmentTests {

        @Test
        @DisplayName("Should assess transaction with valid fraud:detect scope")
        void shouldAssessTransactionWithValidScope() {
            // Given
            AssessTransactionRiskCommand command = buildLowRiskCommand(TransactionId.generate());

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
            when(mlServicePort.predict(any(Transaction.class)))
                    .thenReturn(mockHighRiskPrediction());

            AssessTransactionRiskCommand command = buildHighRiskCommand(TransactionId.generate());

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
            when(mlServicePort.predict(any(Transaction.class)))
                    .thenReturn(mockCriticalRiskPrediction());

            AssessTransactionRiskCommand command = buildCriticalRiskCommand(TransactionId.generate());

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
            String invalidRequestBody = """
                {
                    "transactionId": null,
                    "accountId": null,
                    "amount": -100,
                    "currency": "USD"
                }
                """;

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
    class AuthorizationTests {

        @Test
        @DisplayName("Should reject transaction assessment without fraud:detect scope")
        void shouldRejectAssessmentWithoutDetectScope() {
            AssessTransactionRiskCommand command = buildLowRiskCommand(TransactionId.generate());

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
            AssessTransactionRiskCommand command = buildLowRiskCommand(TransactionId.generate());

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
            AssessTransactionRiskCommand command = buildLowRiskCommand(TransactionId.generate());

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
    class AssessmentRetrievalTests {

        @Test
        @DisplayName("Should get assessment with fraud:read scope")
        void shouldGetAssessmentWithReadScope() {
            AssessTransactionRiskCommand command = buildLowRiskCommand(TransactionId.generate());

            webTestClient.post()
                    .uri("/fraud/assessments")
                    .header(HttpHeaders.AUTHORIZATION, toBearerToken(detectorToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(command)
                    .exchange()
                    .expectStatus().isOk();

            UUID transactionId = command.transactionId();

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
            AssessTransactionRiskCommand command = buildLowRiskCommand(TransactionId.generate());

            webTestClient.post()
                    .uri("/fraud/assessments")
                    .header(HttpHeaders.AUTHORIZATION, toBearerToken(detectorToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(command)
                    .exchange()
                    .expectStatus().isOk();

            UUID transactionId = command.transactionId();

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
            UUID nonExistentTransactionId = UUID.randomUUID();

            webTestClient.get()
                    .uri("/fraud/assessments/{transactionId}", nonExistentTransactionId)
                    .header(HttpHeaders.AUTHORIZATION, toBearerToken(analystToken))
                    .exchange()
                    .expectStatus().isNotFound();
        }

        @Test
        @DisplayName("Should reject get request without authentication")
        void shouldRejectGetRequestWithoutAuth() {
            UUID transactionId = UUID.randomUUID();

            webTestClient.get()
                    .uri("/fraud/assessments/{transactionId}", transactionId)
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }

    @Nested
    @DisplayName("Public Endpoints")
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
    class PaginatedAssessmentSearchTests {

        @Test
        @DisplayName("Should find assessments by risk level with fraud:read scope")
        void shouldFindAssessmentsByRiskLevel() {

            createAssessmentWithRiskLevel(TransactionRiskLevel.HIGH);
            createAssessmentWithRiskLevel(TransactionRiskLevel.HIGH);
            createAssessmentWithRiskLevel(TransactionRiskLevel.HIGH);
            createAssessmentWithRiskLevel(TransactionRiskLevel.MEDIUM);
            createAssessmentWithRiskLevel(TransactionRiskLevel.LOW);

            Integer count = countRiskAssessments();
            assertThat(count).isEqualTo(5);

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
        @DisplayName("Should filter assessments by fromDate date")
        void shouldFilterAssessmentsByFromDate() {
            Instant yesterday = Instant.now().minus(1, ChronoUnit.DAYS);
            Instant twoDaysAgo = Instant.now().minus(2, ChronoUnit.DAYS);

            createAssessmentWithRiskLevel(TransactionRiskLevel.LOW, twoDaysAgo);
            createAssessmentWithRiskLevel(TransactionRiskLevel.MEDIUM, yesterday);
            createAssessmentWithRiskLevel(TransactionRiskLevel.HIGH, Instant.now());

            Integer count = countRiskAssessments();
            assertThat(count).isEqualTo(3);

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
            Instant yesterday = Instant.now().minus(1, ChronoUnit.DAYS);
            Instant twoDaysAgo = Instant.now().minus(2, ChronoUnit.DAYS);

            createAssessmentWithRiskLevel(TransactionRiskLevel.HIGH, twoDaysAgo);
            createAssessmentWithRiskLevel(TransactionRiskLevel.HIGH, yesterday);
            createAssessmentWithRiskLevel(TransactionRiskLevel.HIGH, Instant.now());
            createAssessmentWithRiskLevel(TransactionRiskLevel.MEDIUM, yesterday);

            Integer count = countRiskAssessments();
            assertThat(count).isEqualTo(4);

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
            for (int i = 0; i < 5; i++) {
                createAssessmentWithRiskLevel(TransactionRiskLevel.HIGH);
            }

            Integer count = countRiskAssessments();
            assertThat(count).isEqualTo(5);

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

            // When & Then - Request second page
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

            // When & Then - Request last page
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
            Instant time1 = Instant.now().minus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS);
            Instant time2 = Instant.now().minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS);
            Instant time3 = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS);

            createAssessmentWithRiskLevel(TransactionRiskLevel.HIGH, time1);
            createAssessmentWithRiskLevel(TransactionRiskLevel.HIGH, time3);
            createAssessmentWithRiskLevel(TransactionRiskLevel.HIGH, time2);

            Integer count = countRiskAssessments();
            assertThat(count).isEqualTo(3);

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
            createAssessmentWithRiskLevel(TransactionRiskLevel.LOW);
            createAssessmentWithRiskLevel(TransactionRiskLevel.LOW);

            Integer count = countRiskAssessments();
            assertThat(count).isEqualTo(2);

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
                    .jsonPath("$.page.totalPages").isEqualTo(0)
                    .jsonPath("$.content.length()").isEqualTo(0);
        }

        private Integer countRiskAssessments() {
            return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM risk_assessments", Integer.class);
        }

        private List<RiskAssessmentEntity> getRiskAssessments() {
            return jdbcTemplate.query("SELECT * FROM risk_assessments", new BeanPropertyRowMapper<>(RiskAssessmentEntity.class));
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
            createAssessmentWithRiskLevel(TransactionRiskLevel.HIGH);

            Integer count = countRiskAssessments();
            assertThat(count).isEqualTo(1);

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
            createAssessmentWithRiskLevel(TransactionRiskLevel.HIGH);
            createAssessmentWithRiskLevel(TransactionRiskLevel.HIGH);

            Integer count = countRiskAssessments();
            assertThat(count).isEqualTo(2);

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
                    .jsonPath("$.page.number").isEqualTo(10)
                    .jsonPath("$.content.length()").isEqualTo(0);
        }

        @Test
        @DisplayName("Should search with only fromDate parameter")
        void shouldSearchWithOnlyFromDate() {
            Instant yesterday = Instant.now().minus(1, ChronoUnit.DAYS);

            createAssessmentWithRiskLevel(TransactionRiskLevel.LOW, yesterday);
            createAssessmentWithRiskLevel(TransactionRiskLevel.CRITICAL, Instant.now());

            Integer riskAssessmentCount = countRiskAssessments();
            assertThat(riskAssessmentCount).isEqualTo(2);

            webTestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/fraud/assessments")
                            .queryParam("fromDate", yesterday.toString())
                            .queryParam("page", 0)
                            .queryParam("size", 10)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, toBearerToken(analystToken))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content").isArray()
                    .jsonPath("$.page.totalElements").value(count ->
                            assertThat((Integer) count).isGreaterThanOrEqualTo(2));
        }
    }

    // ========================================
    // Helper Methods
    // ========================================

    /**
     * Obtain access token fromDate Keycloak using password grant with RestClient
     */
    private String obtainAccessToken(String username, String password) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "password");
        formData.add("client_id", CLIENT_ID);
        formData.add("client_secret", CLIENT_SECRET);
        formData.add("username", username);
        formData.add("password", password);
        formData.add("scope", "openid profile email");

        try {
            TokenResponse response = restClient.post()
                    .uri(getTokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(TokenResponse.class);

            if (response != null && response.accessToken() != null) {
                return response.accessToken();
            }

            throw new RuntimeException("Failed to obtain access token - null response");
        } catch (Exception e) {
            throw new RuntimeException("Failed to obtain access token for user: " + username, e);
        }
    }

    /**
     * Convert access token to Bearer token format
     */
    private String toBearerToken(String accessToken) {
        return "Bearer " + accessToken;
    }

    /**
     * Record for Keycloak token response
     */
    private record TokenResponse(
            String access_token,
            String token_type,
            Integer expires_in,
            String refresh_token,
            String scope
    ) {
        String accessToken() {
            return access_token;
        }
    }

    private void createAssessmentWithRiskLevel(TransactionRiskLevel riskLevel) {
        createAssessmentWithRiskLevel(riskLevel, Instant.now());
    }

    private void createAssessmentWithRiskLevel(TransactionRiskLevel riskLevel, Instant assessmentTime) {
        MLPrediction prediction = findMLPrediction(riskLevel);
        when(mlServicePort.predict(any(Transaction.class))).thenReturn(prediction);

        AssessTransactionRiskCommand command = buildAssessTransactionRiskCommand(riskLevel, TransactionId.generate(), assessmentTime);

        webTestClient.post()
                .uri("/fraud/assessments")
                .header(HttpHeaders.AUTHORIZATION, toBearerToken(detectorToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(command)
                .exchange()
                .expectStatus().isOk();

        // Manually update the assessment time in DB
        RiskAssessment assessment = findByTransactionId(TransactionId.of(command.transactionId()));

        RiskAssessment updatedAssessment = buildUpdatedAssessment(assessmentTime, assessment);


        riskAssessmentRepository.save(updatedAssessment);
    }

    private static RiskAssessment buildUpdatedAssessment(Instant assessmentTime, RiskAssessment assessment) {
        return new RiskAssessment(
                assessment.getAssessmentId(),
                assessment.getTransactionId(),
                assessment.getRiskScore(),
                assessment.getRuleEvaluations(),
                assessment.getMlPrediction(),
                assessmentTime, assessment.getDecision());
    }

    private RiskAssessment findByTransactionId(TransactionId transactionId) {
        return riskAssessmentRepository
                .findByTransactionId(transactionId)
                .orElseThrow();
    }

    private MLPrediction findMLPrediction(TransactionRiskLevel riskLevel) {
        return switch (riskLevel) {
            case LOW -> FraudDetectionApplicationServiceIntegrationTest.mockLowRiskPrediction();
            case MEDIUM -> FraudDetectionApplicationServiceIntegrationTest.mockMediumRiskPrediction();
            case HIGH -> FraudDetectionApplicationServiceIntegrationTest.mockHighRiskPrediction();
            case CRITICAL -> mockCriticalRiskPrediction();
        };
    }

    private AssessTransactionRiskCommand buildAssessTransactionRiskCommand(TransactionRiskLevel riskLevel, TransactionId transactionId) {
        return buildAssessTransactionRiskCommand(riskLevel, transactionId, Instant.now());
    }

    private AssessTransactionRiskCommand buildAssessTransactionRiskCommand(TransactionRiskLevel riskLevel, TransactionId transactionId, Instant timestamp) {
        return switch (riskLevel) {
            case LOW -> FraudDetectionApplicationServiceIntegrationTest.buildLowRiskCommand(transactionId, timestamp);
            case MEDIUM -> FraudDetectionApplicationServiceIntegrationTest.buildMediumRiskCommand(transactionId, timestamp);
            case HIGH -> FraudDetectionApplicationServiceIntegrationTest.buildHighRiskCommand(transactionId, timestamp);
            case CRITICAL -> FraudDetectionApplicationServiceIntegrationTest.buildCriticalRiskCommand(transactionId, timestamp);
        };
    }

    /**
     * Create assessment with specific timestamp
     */
//    private void createAssessmentWithTimestamp(Instant timestamp) {
//        AssessTransactionRiskCommand command = AssessTransactionRiskCommand.builder()
//                .transactionId(UUID.randomUUID())
//                .accountId("ACC-" + System.currentTimeMillis())
//                .amount(new BigDecimal("1500.00"))
//                .currency("USD")
//                .type("PURCHASE")
//                .channel("ONLINE")
//                .merchantId("MERCHANT-001")
//                .merchantName("Test Merchant")
//                .merchantCategory("RETAIL")
//                .location(new LocationDto(
//                        40.7128,
//                        -74.0060,
//                        "US",
//                        "New York",
//                        timestamp
//                ))
//                .deviceId("DEVICE-001")
//                .transactionTimestamp(timestamp)
//                .build();
//
//        webTestClient.post()
//                .uri("/fraud/assessments")
//                .header(HttpHeaders.AUTHORIZATION, toBearerToken(detectorToken))
//                .contentType(MediaType.APPLICATION_JSON)
//                .bodyValue(command)
//                .exchange()
//                .expectStatus().isOk();
//    }

    /**
     * Create assessment with specific risk level and timestamp
     */
//    private void createAssessmentWithRiskLevelAndTimestamp(String riskLevel, Instant timestamp) {
//        // Configure ML mock based on desired risk level
//        MLPrediction prediction = findMLPrediction(riskLevel);
//
//        when(mlServicePort.predict(any(Transaction.class))).thenReturn(prediction);
//
//        // Use appropriate command builder based on risk level
//        // HIGH and CRITICAL require transaction characteristics that trigger business rules
//        AssessTransactionRiskCommand command = switch (riskLevel) {
//            case "HIGH" -> AssessTransactionRiskCommand.builder()
//                    .transactionId(UUID.randomUUID())
//                    .accountId("ACC-HIGH-" + System.currentTimeMillis())
//                    .amount(new BigDecimal("50000.01"))  // Triggers high amount rule
//                    .currency("USD")
//                    .type("TRANSFER")
//                    .channel("ONLINE")
//                    .merchantId("MERCHANT-003")
//                    .merchantName("Unknown Vendor")
//                    .merchantCategory("OTHER")
//                    .location(new LocationDto(40.7128, -74.0060, "US", "New York", timestamp))
//                    .deviceId("DEVICE-003")
//                    .transactionTimestamp(timestamp)
//                    .build();
//            case "CRITICAL" -> AssessTransactionRiskCommand.builder()
//                    .transactionId(UUID.randomUUID())
//                    .accountId("ACC-CRITICAL-" + System.currentTimeMillis())
//                    .amount(new BigDecimal("100000.01"))  // Triggers very high amount rule
//                    .currency("USD")
//                    .type("TRANSFER")
//                    .channel("ONLINE")
//                    .merchantId("MERCHANT-UNKNOWN")
//                    .merchantName("Suspicious Vendor")
//                    .merchantCategory("HIGH_RISK")
//                    .location(new LocationDto(40.7128, -74.0060, "US", "New York", timestamp))
//                    .deviceId("DEVICE-CRITICAL")
//                    .transactionTimestamp(timestamp)
//                    .build();
//            default -> AssessTransactionRiskCommand.builder()
//                    .transactionId(UUID.randomUUID())
//                    .accountId("ACC-" + System.currentTimeMillis())
//                    .amount(new BigDecimal("1500.00"))  // Normal amount, no rules triggered
//                    .currency("USD")
//                    .type("PURCHASE")
//                    .channel("ONLINE")
//                    .merchantId("MERCHANT-001")
//                    .merchantName("Test Merchant")
//                    .merchantCategory("RETAIL")
//                    .location(new LocationDto(40.7128, -74.0060, "US", "New York", timestamp))
//                    .deviceId("DEVICE-001")
//                    .transactionTimestamp(timestamp)
//                    .build();
//        };
//
//        webTestClient.post()
//                .uri("/fraud/assessments")
//                .header(HttpHeaders.AUTHORIZATION, toBearerToken(detectorToken))
//                .contentType(MediaType.APPLICATION_JSON)
//                .bodyValue(command)
//                .exchange()
//                .expectStatus().isOk();
//
//        // Reset to default mock
//        when(mlServicePort.predict(any(Transaction.class))).thenReturn(mockLowRiskPrediction());
//    }

    // ========================================
    // Mock ML Predictions
    // ========================================

//    private MLPrediction mockLowRiskPrediction() {
//        return new MLPrediction(
//                "test-endpoint",
//                "1.0.0",
//                0.15,
//                0.95,
//                Map.of("amount", 0.3, "velocity", 0.2)
//        );
//    }

//    private MLPrediction mockHighRiskPrediction() {
//        return new MLPrediction(
//                "test-endpoint",
//                "1.0.0",
//                0.78,
//                0.88,
//                Map.of("amount", 0.7, "geographic", 0.6)
//        );
//    }

//    private MLPrediction mockCriticalRiskPrediction() {
//        return new MLPrediction(
//                "test-endpoint",
//                "1.0.0",
//                0.95,
//                0.90,
//                Map.of("amount", 0.9, "velocity", 0.8, "geographic", 0.85)
//        );
//    }

    // ========================================
    // Command Builders
    // ========================================

//    private AssessTransactionRiskCommand buildValidCommand() {
//        return AssessTransactionRiskCommand.builder()
//                .transactionId(UUID.randomUUID())
//                .accountId("ACC-" + System.currentTimeMillis())
//                .amount(new BigDecimal("1500.00"))
//                .currency("USD")
//                .type("PURCHASE")
//                .channel("ONLINE")
//                .merchantId("MERCHANT-001")
//                .merchantName("Test Merchant")
//                .merchantCategory("RETAIL")
//                .location(new LocationDto(
//                        40.7128,
//                        -74.0060,
//                        "US",
//                        "New York",
//                        Instant.now()
//                ))
//                .deviceId("DEVICE-001")
//                .transactionTimestamp(Instant.now())
//                .build();
//    }

//    private AssessTransactionRiskCommand buildHighRiskCommand() {
//        return AssessTransactionRiskCommand.builder()
//                .transactionId(UUID.randomUUID())
//                .accountId("ACC-HIGH-" + System.currentTimeMillis())
//                .amount(new BigDecimal("50000.01"))
//                .currency("USD")
//                .type("TRANSFER")
//                .channel("ONLINE")
//                .merchantId("MERCHANT-003")
//                .merchantName("Unknown Vendor")
//                .merchantCategory("OTHER")
//                .location(new LocationDto(
//                        40.7128,
//                        -74.0060,
//                        "US",
//                        "New York",
//                        Instant.now()
//                ))
//                .deviceId("DEVICE-003")
//                .transactionTimestamp(Instant.now())
//                .build();
//    }

//    private AssessTransactionRiskCommand buildCriticalRiskCommand() {
//        return AssessTransactionRiskCommand.builder()
//                .transactionId(UUID.randomUUID())
//                .accountId("ACC-CRITICAL-" + System.currentTimeMillis())
//                .amount(new BigDecimal("100000.01"))
//                .currency("USD")
//                .type("TRANSFER")
//                .channel("ONLINE")
//                .merchantId("MERCHANT-UNKNOWN")
//                .merchantName("Suspicious Vendor")
//                .merchantCategory("HIGH_RISK")
//                .location(new LocationDto(
//                        40.7128,
//                        -74.0060,
//                        "US",
//                        "New York",
//                        Instant.now()
//                ))
//                .deviceId("DEVICE-CRITICAL")
//                .transactionTimestamp(Instant.now())
//                .build();
//    }
}