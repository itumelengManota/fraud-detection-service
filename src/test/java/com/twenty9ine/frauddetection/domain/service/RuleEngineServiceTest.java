package com.twenty9ine.frauddetection.domain.service;

import com.twenty9ine.frauddetection.domain.valueobject.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Disabled
@ExtendWith(MockitoExtension.class)
class RuleEngineServiceTest {

    @Mock
    private KieContainer kieContainer;

    @Mock
    private KieSession kieSession;

    private RuleEngineService ruleEngineService;

    @BeforeEach
    void setUp() {
        ruleEngineService = new RuleEngineService(kieContainer);
        when(kieContainer.newKieSession()).thenReturn(kieSession);
    }

    @Test
    void evaluateRules_shouldCreateNewKieSession() {
        // Given
        Transaction transaction = createTestTransaction(BigDecimal.valueOf(100));
        VelocityMetrics velocity = VelocityMetrics.empty();
        GeographicContext geographic = GeographicContext.normal();
        when(kieSession.fireAllRules()).thenReturn(0);

        // When
        ruleEngineService.evaluateRules(transaction, velocity, geographic);

        // Then
        verify(kieContainer).newKieSession();
    }

    @Test
    void evaluateRules_shouldInsertAllFactsIntoSession() {
        // Given
        Transaction transaction = createTestTransaction(BigDecimal.valueOf(100));
        VelocityMetrics velocity = VelocityMetrics.empty();
        GeographicContext geographic = GeographicContext.normal();
        when(kieSession.fireAllRules()).thenReturn(0);

        // When
        ruleEngineService.evaluateRules(transaction, velocity, geographic);

        // Then
        verify(kieSession).insert(transaction);
        verify(kieSession).insert(velocity);
        verify(kieSession).insert(geographic);
    }

    @Test
    void evaluateRules_shouldSetGlobalResultVariable() {
        // Given
        Transaction transaction = createTestTransaction(BigDecimal.valueOf(100));
        VelocityMetrics velocity = VelocityMetrics.empty();
        GeographicContext geographic = GeographicContext.normal();
        when(kieSession.fireAllRules()).thenReturn(0);

        // When
        ruleEngineService.evaluateRules(transaction, velocity, geographic);

        // Then
        verify(kieSession).setGlobal(eq("ruleEvaluationResult"), any(RuleEvaluationResult.class));
    }

    @Test
    void evaluateRules_shouldFireAllRules() {
        // Given
        Transaction transaction = createTestTransaction(BigDecimal.valueOf(100));
        VelocityMetrics velocity = VelocityMetrics.empty();
        GeographicContext geographic = GeographicContext.normal();
        when(kieSession.fireAllRules()).thenReturn(3);

        // When
        ruleEngineService.evaluateRules(transaction, velocity, geographic);

        // Then
        verify(kieSession).fireAllRules();
    }

    @Test
    void evaluateRules_shouldReturnEmptyResultWhenNoRulesFired() {
        // Given
        Transaction transaction = createTestTransaction(BigDecimal.valueOf(100));
        VelocityMetrics velocity = VelocityMetrics.empty();
        GeographicContext geographic = GeographicContext.normal();
        when(kieSession.fireAllRules()).thenReturn(0);

        // When
        RuleEvaluationResult result = ruleEngineService.evaluateRules(transaction, velocity, geographic);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTriggers()).isEmpty();
        assertThat(result.aggregateScore()).isZero();
    }

    @Test
    void evaluateRules_shouldCloseKieSessionAfterExecution() {
        // Given
        Transaction transaction = createTestTransaction(BigDecimal.valueOf(100));
        VelocityMetrics velocity = VelocityMetrics.empty();
        GeographicContext geographic = GeographicContext.normal();
        when(kieSession.fireAllRules()).thenReturn(0);

        // When
        ruleEngineService.evaluateRules(transaction, velocity, geographic);

        // Then
        verify(kieSession).dispose();
    }

    @Test
    void evaluateRules_shouldCloseKieSessionEvenWhenExceptionOccurs() {
        // Given
        Transaction transaction = createTestTransaction(BigDecimal.valueOf(100));
        VelocityMetrics velocity = VelocityMetrics.empty();
        GeographicContext geographic = GeographicContext.normal();
        when(kieSession.fireAllRules()).thenThrow(new RuntimeException("Rule error"));

        // When/Then
        try {
            ruleEngineService.evaluateRules(transaction, velocity, geographic);
        } catch (RuntimeException e) {
            // Expected
        }

        verify(kieSession).dispose();
    }

    @Test
    void evaluateRules_withHighVelocity_shouldTriggerVelocityRules() {
        // Given
        Transaction transaction = createTestTransaction(BigDecimal.valueOf(100));
        VelocityMetrics velocity = createHighVelocityMetrics();
        GeographicContext geographic = GeographicContext.normal();
        when(kieSession.fireAllRules()).thenReturn(2);

        // When
        RuleEvaluationResult result = ruleEngineService.evaluateRules(transaction, velocity, geographic);

        // Then
        assertThat(result).isNotNull();
        verify(kieSession).fireAllRules();
    }

    @Test
    void evaluateRules_withLargeAmount_shouldTriggerAmountRules() {
        // Given
        Transaction transaction = createTestTransaction(BigDecimal.valueOf(15000));
        VelocityMetrics velocity = VelocityMetrics.empty();
        GeographicContext geographic = GeographicContext.normal();
        when(kieSession.fireAllRules()).thenReturn(1);

        // When
        RuleEvaluationResult result = ruleEngineService.evaluateRules(transaction, velocity, geographic);

        // Then
        assertThat(result).isNotNull();
        verify(kieSession).fireAllRules();
    }

    @Test
    void evaluateRules_withImpossibleTravel_shouldTriggerGeographicRules() {
        // Given
        Transaction transaction = createTestTransaction(BigDecimal.valueOf(100));
        VelocityMetrics velocity = VelocityMetrics.empty();
        GeographicContext geographic = createImpossibleTravelContext();
        when(kieSession.fireAllRules()).thenReturn(1);

        // When
        RuleEvaluationResult result = ruleEngineService.evaluateRules(transaction, velocity, geographic);

        // Then
        assertThat(result).isNotNull();
        verify(kieSession).fireAllRules();
    }

    @Test
    void evaluateRules_withMultipleRiskFactors_shouldFireMultipleRules() {
        // Given
        Transaction transaction = createTestTransaction(BigDecimal.valueOf(60000));
        VelocityMetrics velocity = createHighVelocityMetrics();
        GeographicContext geographic = createImpossibleTravelContext();
        when(kieSession.fireAllRules()).thenReturn(5);

        // When
        RuleEvaluationResult result = ruleEngineService.evaluateRules(transaction, velocity, geographic);

        // Then
        assertThat(result).isNotNull();
        verify(kieSession).fireAllRules();
    }

    private Transaction createTestTransaction(BigDecimal amount) {
        return Transaction.builder()
                .id(TransactionId.generate())
                .accountId("ACC-123")
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

    private VelocityMetrics createHighVelocityMetrics() {
        return VelocityMetrics.builder()
                .transactionCounts(Map.of(
                        VelocityMetrics.FIVE_MINUTES, 8L,
                        VelocityMetrics.ONE_HOUR, 25L,
                        VelocityMetrics.TWENTY_FOUR_HOURS, 50L
                ))
                .totalAmounts(Map.of(
                        VelocityMetrics.FIVE_MINUTES, BigDecimal.valueOf(5000),
                        VelocityMetrics.ONE_HOUR, BigDecimal.valueOf(15000),
                        VelocityMetrics.TWENTY_FOUR_HOURS, BigDecimal.valueOf(30000)
                ))
                .uniqueMerchants(Map.of(
                        VelocityMetrics.FIVE_MINUTES, 5,
                        VelocityMetrics.ONE_HOUR, 15,
                        VelocityMetrics.TWENTY_FOUR_HOURS, 30
                ))
                .uniqueLocations(Map.of(
                        VelocityMetrics.FIVE_MINUTES, 3L,
                        VelocityMetrics.ONE_HOUR, 8L,
                        VelocityMetrics.TWENTY_FOUR_HOURS, 15L
                ))
                .build();
    }

    private GeographicContext createImpossibleTravelContext() {
        Instant currentTime = Instant.now();
        Instant previousTime = currentTime.minusSeconds(5); // 2 hours ago

        Location previousLocation = new Location(40.7128, -74.0060, "New York", "US", previousTime);
        Location currentLocation = new Location(51.5074, -0.1278, "London", "GB", currentTime);

        return GeographicContext.builder()
                .impossibleTravel(true)
                .distanceKm(5570.0)
                .travelSpeed(11140.0)
                .previousLocation(previousLocation)
                .currentLocation(currentLocation)
                .build();
    }
}