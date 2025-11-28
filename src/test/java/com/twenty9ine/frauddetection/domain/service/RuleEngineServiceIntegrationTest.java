package com.twenty9ine.frauddetection.domain.service;

import com.twenty9ine.frauddetection.domain.valueobject.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled
class RuleEngineServiceIntegrationTest {

    private RuleEngineService ruleEngineService;

    @BeforeEach
    void setUp() {
        KieServices kieServices = KieServices.Factory.get();
        KieContainer kieContainer = kieServices.getKieClasspathContainer();
        ruleEngineService = new RuleEngineService(kieContainer);
    }

    @Test
    void evaluateRules_withNormalTransaction_shouldNotTriggerAnyRules() {
        // Given
        Transaction transaction = createTestTransaction(BigDecimal.valueOf(100));
        VelocityMetrics velocity = VelocityMetrics.empty();
        GeographicContext geographic = GeographicContext.normal();

        // When
        RuleEvaluationResult result = ruleEngineService.evaluateRules(transaction, velocity, geographic);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTriggers()).isEmpty();
        assertThat(result.aggregateScore()).isZero();
    }

    @Test
    void evaluateRules_withLargeAmount_shouldTriggerLargeAmountRule() {
        // Given
        Transaction transaction = createTestTransaction(BigDecimal.valueOf(12000));
        VelocityMetrics velocity = VelocityMetrics.empty();
        GeographicContext geographic = GeographicContext.normal();

        // When
        RuleEvaluationResult result = ruleEngineService.evaluateRules(transaction, velocity, geographic);

        // Then
        assertThat(result.getTriggers())
            .hasSize(1)
            .extracting(RuleTrigger::ruleName)
            .containsExactly("LARGE_AMOUNT");
        assertThat(result.getTriggers().getFirst().impact()).isEqualTo(RiskImpact.MEDIUM);
        assertThat(result.aggregateScore()).isEqualTo(25.0);
    }

    @Test
    void evaluateRules_withVeryLargeAmount_shouldTriggerBothAmountRules() {
        // Given
        Transaction transaction = createTestTransaction(BigDecimal.valueOf(55000));
        VelocityMetrics velocity = VelocityMetrics.empty();
        GeographicContext geographic = GeographicContext.normal();

        // When
        RuleEvaluationResult result = ruleEngineService.evaluateRules(transaction, velocity, geographic);

        // Then
        assertThat(result.getTriggers())
            .hasSize(2)
            .extracting(RuleTrigger::ruleName)
            .containsExactlyInAnyOrder("LARGE_AMOUNT", "VERY_LARGE_AMOUNT");
        assertThat(result.aggregateScore()).isEqualTo(65.0); // MEDIUM (25) + HIGH (40)
    }

    @Test
    void evaluateRules_withHighVelocity5Minutes_shouldTriggerVelocityRule() {
        // Given
        Transaction transaction = createTestTransaction(BigDecimal.valueOf(100));
        VelocityMetrics velocity = VelocityMetrics.builder()
            .transactionCounts(Map.of(
                VelocityMetrics.FIVE_MINUTES, 8L,
                VelocityMetrics.ONE_HOUR, 10L,
                VelocityMetrics.TWENTY_FOUR_HOURS, 15L
            ))
            .totalAmounts(Map.of(
                VelocityMetrics.FIVE_MINUTES, BigDecimal.valueOf(800),
                VelocityMetrics.ONE_HOUR, BigDecimal.valueOf(1000),
                VelocityMetrics.TWENTY_FOUR_HOURS, BigDecimal.valueOf(1500)
            ))
            .uniqueMerchants(Map.of(
                VelocityMetrics.FIVE_MINUTES, 3,
                VelocityMetrics.ONE_HOUR, 5,
                VelocityMetrics.TWENTY_FOUR_HOURS, 8
            ))
            .uniqueLocations(Map.of(
                VelocityMetrics.FIVE_MINUTES, 2L,
                VelocityMetrics.ONE_HOUR, 4L,
                VelocityMetrics.TWENTY_FOUR_HOURS, 6L
            ))
            .build();
        GeographicContext geographic = GeographicContext.normal();

        // When
        RuleEvaluationResult result = ruleEngineService.evaluateRules(transaction, velocity, geographic);

        // Then
        assertThat(result.getTriggers())
            .hasSize(1)
            .extracting(RuleTrigger::ruleName)
            .containsExactly("VELOCITY_5MIN");
        assertThat(result.getTriggers().get(0).impact()).isEqualTo(RiskImpact.HIGH);
        assertThat(result.aggregateScore()).isEqualTo(40.0);
    }

    @Test
    void evaluateRules_withExtremeVelocity1Hour_shouldTriggerCriticalVelocityRule() {
        // Given
        Transaction transaction = createTestTransaction(BigDecimal.valueOf(100));
        VelocityMetrics velocity = VelocityMetrics.builder()
            .transactionCounts(Map.of(
                VelocityMetrics.FIVE_MINUTES, 8L,
                VelocityMetrics.ONE_HOUR, 25L,
                VelocityMetrics.TWENTY_FOUR_HOURS, 50L
            ))
            .totalAmounts(Map.of(
                VelocityMetrics.FIVE_MINUTES, BigDecimal.valueOf(2000),
                VelocityMetrics.ONE_HOUR, BigDecimal.valueOf(10000),
                VelocityMetrics.TWENTY_FOUR_HOURS, BigDecimal.valueOf(25000)
            ))
            .uniqueMerchants(Map.of(
                VelocityMetrics.FIVE_MINUTES, 5,
                VelocityMetrics.ONE_HOUR, 15,
                VelocityMetrics.TWENTY_FOUR_HOURS, 30
            ))
            .uniqueLocations(Map.of(
                VelocityMetrics.FIVE_MINUTES, 3L,
                VelocityMetrics.ONE_HOUR, 10L,
                VelocityMetrics.TWENTY_FOUR_HOURS, 20L
            ))
            .build();
        GeographicContext geographic = GeographicContext.normal();

        // When
        RuleEvaluationResult result = ruleEngineService.evaluateRules(transaction, velocity, geographic);

        // Then
        assertThat(result.getTriggers())
            .hasSize(2)
            .extracting(RuleTrigger::ruleName)
            .containsExactlyInAnyOrder("VELOCITY_5MIN", "VELOCITY_1HOUR");
        assertThat(result.aggregateScore()).isEqualTo(100.0); // HIGH (40) + CRITICAL (60)
    }

    @Test
    void evaluateRules_withImpossibleTravel_shouldTriggerGeographicRule() {
        // Given
        Transaction transaction = createTestTransaction(BigDecimal.valueOf(100));
        VelocityMetrics velocity = VelocityMetrics.empty();

        Instant currentTime = Instant.now();
        Instant previousTime = currentTime.minusSeconds(5);

        Location previousLocation = new Location(40.7128, -74.0060, "New York", "US", previousTime);
        Location currentLocation = new Location(51.5074, -0.1278, "London", "GB", currentTime);

        GeographicContext geographic = GeographicContext.builder()
            .impossibleTravel(true)
            .distanceKm(5570.0)
            .travelSpeed(11140.0)
            .previousLocation(previousLocation)
            .currentLocation(currentLocation)
            .build();

        // When
        RuleEvaluationResult result = ruleEngineService.evaluateRules(transaction, velocity, geographic);

        // Then
        assertThat(result.getTriggers())
            .hasSize(1)
            .extracting(RuleTrigger::ruleName)
            .containsExactly("IMPOSSIBLE_TRAVEL");
        assertThat(result.getTriggers().getFirst().impact()).isEqualTo(RiskImpact.CRITICAL);
        assertThat(result.getTriggers().getFirst().triggeredValue()).isEqualTo(11140.0);
        assertThat(result.aggregateScore()).isEqualTo(60.0);
    }

    @Test
    void evaluateRules_withMultipleRiskFactors_shouldTriggerMultipleRules() {
        // Given
        Transaction transaction = createTestTransaction(BigDecimal.valueOf(60000));
        VelocityMetrics velocity = VelocityMetrics.builder()
            .transactionCounts(Map.of(
                VelocityMetrics.FIVE_MINUTES, 10L,
                VelocityMetrics.ONE_HOUR, 30L,
                VelocityMetrics.TWENTY_FOUR_HOURS, 80L
            ))
            .totalAmounts(Map.of(
                VelocityMetrics.FIVE_MINUTES, BigDecimal.valueOf(5000),
                VelocityMetrics.ONE_HOUR, BigDecimal.valueOf(20000),
                VelocityMetrics.TWENTY_FOUR_HOURS, BigDecimal.valueOf(100000)
            ))
            .uniqueMerchants(Map.of(
                VelocityMetrics.FIVE_MINUTES, 7,
                VelocityMetrics.ONE_HOUR, 20,
                VelocityMetrics.TWENTY_FOUR_HOURS, 50
            ))
            .uniqueLocations(Map.of(
                VelocityMetrics.FIVE_MINUTES, 5L,
                VelocityMetrics.ONE_HOUR, 15L,
                VelocityMetrics.TWENTY_FOUR_HOURS, 40L
            ))
            .build();

        Instant currentTime = Instant.now();
        Instant previousTime = currentTime.minusSeconds(10);

        Location previousLocation = new Location(40.7128, -74.0060, "New York", "US", previousTime);
        Location currentLocation = new Location(51.5074, -0.1278, "London", "GB", currentTime);
        GeographicContext geographic = GeographicContext.builder()
            .impossibleTravel(true)
            .distanceKm(5570.0)
            .travelSpeed(11140.0)
            .previousLocation(previousLocation)
            .currentLocation(currentLocation)
            .build();

        // When
        RuleEvaluationResult result = ruleEngineService.evaluateRules(transaction, velocity, geographic);

        // Then
        assertThat(result.getTriggers())
            .hasSize(5)
            .extracting(RuleTrigger::ruleName)
            .containsExactlyInAnyOrder(
                "LARGE_AMOUNT",
                "VERY_LARGE_AMOUNT",
                "VELOCITY_5MIN",
                "VELOCITY_1HOUR",
                "IMPOSSIBLE_TRAVEL"
            );
        assertThat(result.aggregateScore()).isEqualTo(225.0);
        // MEDIUM (25) + HIGH (40) + HIGH (40) + CRITICAL (60) + CRITICAL (60)
    }

    @Test
    void evaluateRules_withBoundaryAmount10000_shouldTriggerLargeAmountRule() {
        // Given
        Transaction transaction = createTestTransaction(BigDecimal.valueOf(10001));
        VelocityMetrics velocity = VelocityMetrics.empty();
        GeographicContext geographic = GeographicContext.normal();

        // When
        RuleEvaluationResult result = ruleEngineService.evaluateRules(transaction, velocity, geographic);

        // Then
        assertThat(result.getTriggers())
            .hasSize(1)
            .extracting(RuleTrigger::ruleName)
            .containsExactly("LARGE_AMOUNT");
    }

    @Test
    void evaluateRules_withBoundaryAmount50000_shouldTriggerBothAmountRules() {
        // Given
        Transaction transaction = createTestTransaction(BigDecimal.valueOf(50001));
        VelocityMetrics velocity = VelocityMetrics.empty();
        GeographicContext geographic = GeographicContext.normal();

        // When
        RuleEvaluationResult result = ruleEngineService.evaluateRules(transaction, velocity, geographic);

        // Then
        assertThat(result.getTriggers())
            .hasSize(2)
            .extracting(RuleTrigger::ruleName)
            .containsExactlyInAnyOrder("LARGE_AMOUNT", "VERY_LARGE_AMOUNT");
    }

    @Test
    void evaluateRules_withExactly6Transactions5Minutes_shouldTriggerVelocityRule() {
        // Given
        Transaction transaction = createTestTransaction(BigDecimal.valueOf(100));
        VelocityMetrics velocity = VelocityMetrics.builder()
            .transactionCounts(Map.of(
                VelocityMetrics.FIVE_MINUTES, 6L,
                VelocityMetrics.ONE_HOUR, 10L,
                VelocityMetrics.TWENTY_FOUR_HOURS, 15L
            ))
            .totalAmounts(Map.of(
                VelocityMetrics.FIVE_MINUTES, BigDecimal.valueOf(600),
                VelocityMetrics.ONE_HOUR, BigDecimal.valueOf(1000),
                VelocityMetrics.TWENTY_FOUR_HOURS, BigDecimal.valueOf(1500)
            ))
            .uniqueMerchants(Map.of(
                VelocityMetrics.FIVE_MINUTES, 3,
                VelocityMetrics.ONE_HOUR, 5,
                VelocityMetrics.TWENTY_FOUR_HOURS, 8
            ))
            .uniqueLocations(Map.of(
                VelocityMetrics.FIVE_MINUTES, 2L,
                VelocityMetrics.ONE_HOUR, 4L,
                VelocityMetrics.TWENTY_FOUR_HOURS, 6L
            ))
            .build();
        GeographicContext geographic = GeographicContext.normal();

        // When
        RuleEvaluationResult result = ruleEngineService.evaluateRules(transaction, velocity, geographic);

        // Then
        assertThat(result.getTriggers())
            .hasSize(1)
            .extracting(RuleTrigger::ruleName)
            .containsExactly("VELOCITY_5MIN");
    }

    @Test
    void evaluateRules_withExactly21Transactions1Hour_shouldTriggerBothVelocityRules() {
        // Given
        Transaction transaction = createTestTransaction(BigDecimal.valueOf(100));
        VelocityMetrics velocity = VelocityMetrics.builder()
            .transactionCounts(Map.of(
                VelocityMetrics.FIVE_MINUTES, 7L,
                VelocityMetrics.ONE_HOUR, 21L,
                VelocityMetrics.TWENTY_FOUR_HOURS, 30L
            ))
            .totalAmounts(Map.of(
                VelocityMetrics.FIVE_MINUTES, BigDecimal.valueOf(700),
                VelocityMetrics.ONE_HOUR, BigDecimal.valueOf(2100),
                VelocityMetrics.TWENTY_FOUR_HOURS, BigDecimal.valueOf(3000)
            ))
            .uniqueMerchants(Map.of(
                VelocityMetrics.FIVE_MINUTES, 4,
                VelocityMetrics.ONE_HOUR, 12,
                VelocityMetrics.TWENTY_FOUR_HOURS, 20
            ))
            .uniqueLocations(Map.of(
                VelocityMetrics.FIVE_MINUTES, 3L,
                VelocityMetrics.ONE_HOUR, 8L,
                VelocityMetrics.TWENTY_FOUR_HOURS, 15L
            ))
            .build();
        GeographicContext geographic = GeographicContext.normal();

        // When
        RuleEvaluationResult result = ruleEngineService.evaluateRules(transaction, velocity, geographic);

        // Then
        assertThat(result.getTriggers())
            .hasSize(2)
            .extracting(RuleTrigger::ruleName)
            .containsExactlyInAnyOrder("VELOCITY_5MIN", "VELOCITY_1HOUR");
    }

    private Transaction createTestTransaction(BigDecimal amount) {
        return Transaction.builder()
                        .id(TransactionId.generate())
                        .accountId("ACC-TEST-123")
                        .amount(new Money(amount, "USD"))
                        .type(TransactionType.PURCHASE)
                        .channel(Channel.ONLINE)
                        .merchantId("MERCH-001")
                        .merchantName("Test Merchant")
                        .merchantCategory("Electronics")
                        .location(new Location(40.7128, -74.0060, "New York", "US", Instant.now()))
                        .deviceId("DEV-001")
                        .timestamp(Instant.now())
                        .build();
    }
}