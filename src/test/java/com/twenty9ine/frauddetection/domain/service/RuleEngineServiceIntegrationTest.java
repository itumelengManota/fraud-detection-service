package com.twenty9ine.frauddetection.domain.service;

import com.twenty9ine.frauddetection.domain.valueobject.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Map;

import static com.twenty9ine.frauddetection.domain.valueobject.TimeWindow.*;
import static org.assertj.core.api.Assertions.assertThat;

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
            .containsExactly("Large Amount");
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
            .containsExactlyInAnyOrder("Large Amount", "Very Large Amount");
        assertThat(result.aggregateScore()).isEqualTo(65.0); // MEDIUM (25) + HIGH (40)
    }

    @Test
    void evaluateRules_withHighVelocity5Minutes_shouldTriggerVelocityRule() {
        // Given
        Transaction transaction = createTestTransaction(BigDecimal.valueOf(100));
        VelocityMetrics velocity = VelocityMetrics.builder()
            .transactionCounts(Map.of(
                FIVE_MINUTES, 8L,
                ONE_HOUR, 10L,
                TWENTY_FOUR_HOURS, 15L
            ))
            .totalAmounts(Map.of(
                FIVE_MINUTES, BigDecimal.valueOf(800),
                ONE_HOUR, BigDecimal.valueOf(1000),
                TWENTY_FOUR_HOURS, BigDecimal.valueOf(1500)
            ))
            .uniqueMerchants(Map.of(
                FIVE_MINUTES, 3L,
                ONE_HOUR, 5L,
                TWENTY_FOUR_HOURS, 8L
            ))
            .uniqueLocations(Map.of(
                FIVE_MINUTES, 2L,
                ONE_HOUR, 4L,
                TWENTY_FOUR_HOURS, 6L
            ))
            .build();
        GeographicContext geographic = GeographicContext.normal();

        // When
        RuleEvaluationResult result = ruleEngineService.evaluateRules(transaction, velocity, geographic);

        // Then
        assertThat(result.getTriggers())
            .hasSize(1)
            .extracting(RuleTrigger::ruleName)
            .containsExactly("High Velocity 5min");
        assertThat(result.getTriggers().getFirst().impact()).isEqualTo(RiskImpact.HIGH);
        assertThat(result.aggregateScore()).isEqualTo(40.0);
    }

    @Test
    void evaluateRules_withExtremeVelocity1Hour_shouldTriggerCriticalVelocityRule() {
        // Given
        Transaction transaction = createTestTransaction(BigDecimal.valueOf(100));
        VelocityMetrics velocity = VelocityMetrics.builder()
            .transactionCounts(Map.of(
                FIVE_MINUTES, 8L,
                ONE_HOUR, 25L,
                TWENTY_FOUR_HOURS, 50L
            ))
            .totalAmounts(Map.of(
                FIVE_MINUTES, BigDecimal.valueOf(2000),
                ONE_HOUR, BigDecimal.valueOf(10000),
                TWENTY_FOUR_HOURS, BigDecimal.valueOf(25000)
            ))
            .uniqueMerchants(Map.of(
                FIVE_MINUTES, 5L,
                ONE_HOUR, 15L,
                TWENTY_FOUR_HOURS, 30L
            ))
            .uniqueLocations(Map.of(
                FIVE_MINUTES, 3L,
                ONE_HOUR, 10L,
                TWENTY_FOUR_HOURS, 20L
            ))
            .build();
        GeographicContext geographic = GeographicContext.normal();

        // When
        RuleEvaluationResult result = ruleEngineService.evaluateRules(transaction, velocity, geographic);

        // Then
        assertThat(result.getTriggers())
            .hasSize(2)
            .extracting(RuleTrigger::ruleName)
            .containsExactlyInAnyOrder("High Velocity 5min", "Extreme Velocity 1hr");
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
            .isImpossibleTravel(true)
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
            .containsExactly("Impossible Travel");
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
                FIVE_MINUTES, 10L,
                ONE_HOUR, 30L,
                TWENTY_FOUR_HOURS, 80L
            ))
            .totalAmounts(Map.of(
                FIVE_MINUTES, BigDecimal.valueOf(5000),
                ONE_HOUR, BigDecimal.valueOf(20000),
                TWENTY_FOUR_HOURS, BigDecimal.valueOf(100000)
            ))
            .uniqueMerchants(Map.of(
                FIVE_MINUTES, 7L,
                ONE_HOUR, 20L,
                TWENTY_FOUR_HOURS, 50L
            ))
            .uniqueLocations(Map.of(
                FIVE_MINUTES, 5L,
                ONE_HOUR, 15L,
                TWENTY_FOUR_HOURS, 40L
            ))
            .build();

        Instant currentTime = Instant.now();
        Instant previousTime = currentTime.minusSeconds(10);

        Location previousLocation = new Location(40.7128, -74.0060, "New York", "US", previousTime);
        Location currentLocation = new Location(51.5074, -0.1278, "London", "GB", currentTime);
        GeographicContext geographic = GeographicContext.builder()
            .isImpossibleTravel(true)
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
                "Large Amount",
                "Very Large Amount",
                "High Velocity 5min",
                "Extreme Velocity 1hr",
                "Impossible Travel"
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
            .containsExactly("Large Amount");
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
            .containsExactlyInAnyOrder("Large Amount", "Very Large Amount");
    }

    @Test
    void evaluateRules_withExactly6Transactions5Minutes_shouldTriggerVelocityRule() {
        // Given
        Transaction transaction = createTestTransaction(BigDecimal.valueOf(100));
        VelocityMetrics velocity = VelocityMetrics.builder()
            .transactionCounts(Map.of(
                FIVE_MINUTES, 6L,
                ONE_HOUR, 10L,
                TWENTY_FOUR_HOURS, 15L
            ))
            .totalAmounts(Map.of(
                FIVE_MINUTES, BigDecimal.valueOf(600),
                ONE_HOUR, BigDecimal.valueOf(1000),
                TWENTY_FOUR_HOURS, BigDecimal.valueOf(1500)
            ))
            .uniqueMerchants(Map.of(
                FIVE_MINUTES, 3L,
                ONE_HOUR, 5L,
                TWENTY_FOUR_HOURS, 8L
            ))
            .uniqueLocations(Map.of(
                FIVE_MINUTES, 2L,
                ONE_HOUR, 4L,
                TWENTY_FOUR_HOURS, 6L
            ))
            .build();
        GeographicContext geographic = GeographicContext.normal();

        // When
        RuleEvaluationResult result = ruleEngineService.evaluateRules(transaction, velocity, geographic);

        // Then
        assertThat(result.getTriggers())
            .hasSize(1)
            .extracting(RuleTrigger::ruleName)
            .containsExactly("High Velocity 5min");
    }

    @Test
    void evaluateRules_withExactly21Transactions1Hour_shouldTriggerBothVelocityRules() {
        // Given
        Transaction transaction = createTestTransaction(BigDecimal.valueOf(100));
        VelocityMetrics velocity = VelocityMetrics.builder()
            .transactionCounts(Map.of(
                FIVE_MINUTES, 7L,
                ONE_HOUR, 21L,
                TWENTY_FOUR_HOURS, 30L
            ))
            .totalAmounts(Map.of(
                FIVE_MINUTES, BigDecimal.valueOf(700),
                ONE_HOUR, BigDecimal.valueOf(2100),
                TWENTY_FOUR_HOURS, BigDecimal.valueOf(3000)
            ))
            .uniqueMerchants(Map.of(
                FIVE_MINUTES, 4L,
                ONE_HOUR, 12L,
                TWENTY_FOUR_HOURS, 20L
            ))
            .uniqueLocations(Map.of(
                FIVE_MINUTES, 3L,
                ONE_HOUR, 8L,
                TWENTY_FOUR_HOURS, 15L
            ))
            .build();
        GeographicContext geographic = GeographicContext.normal();

        // When
        RuleEvaluationResult result = ruleEngineService.evaluateRules(transaction, velocity, geographic);

        // Then
        assertThat(result.getTriggers())
            .hasSize(2)
            .extracting(RuleTrigger::ruleName)
            .containsExactlyInAnyOrder("High Velocity 5min", "Extreme Velocity 1hr");
    }

    private Transaction createTestTransaction(BigDecimal amount) {
        return Transaction.builder()
                        .id(TransactionId.generate())
                        .accountId("ACC-TEST-123")
                        .amount(new Money(amount, Currency.getInstance("USD")))
                        .type(TransactionType.PURCHASE)
                        .channel(Channel.ONLINE)
                .merchant(new Merchant(MerchantId.of("MERCH-001"), "Test Merchant", "Electronics"))
                        .location(new Location(40.7128, -74.0060, "New York", "US", Instant.now()))
                        .deviceId("DEV-001")
                        .timestamp(Instant.now())
                        .build();
    }
}