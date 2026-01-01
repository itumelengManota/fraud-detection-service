package com.twenty9ine.frauddetection;

import com.twenty9ine.frauddetection.application.dto.LocationDto;
import com.twenty9ine.frauddetection.application.port.in.command.AssessTransactionRiskCommand;
import com.twenty9ine.frauddetection.domain.valueobject.MLPrediction;
import com.twenty9ine.frauddetection.domain.valueobject.TransactionId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Centralized factory for creating test data and mock objects.
 * All methods are static and thread-safe for parallel test execution.
 *
 * Performance Benefits:
 * - Eliminates object creation overhead in tests
 * - Provides consistent test data across test classes
 * - Enables parallel test execution without state conflicts
 */
public final class TestDataFactory {

    private TestDataFactory() {
        // Utility class
    }

    // ========================================
    // ML Prediction Mocks (Thread-safe, immutable)
    // ========================================

    private static final MLPrediction LOW_RISK_PREDICTION = new MLPrediction(
            "test-endpoint",
            "1.0.0",
            0.15,
            0.95,
            Map.of("amount", 0.3, "velocity", 0.2)
    );

    private static final MLPrediction MEDIUM_RISK_PREDICTION = new MLPrediction(
            "test-endpoint",
            "1.0.0",
            0.45,
            0.92,
            Map.of("amount", 0.5, "velocity", 0.4)
    );

    private static final MLPrediction HIGH_RISK_PREDICTION = new MLPrediction(
            "test-endpoint",
            "1.0.0",
            0.78,
            0.88,
            Map.of("amount", 0.7, "geographic", 0.6)
    );

    private static final MLPrediction CRITICAL_RISK_PREDICTION = new MLPrediction(
            "test-endpoint",
            "1.0.0",
            0.95,
            0.90,
            Map.of("amount", 0.9, "velocity", 0.8, "geographic", 0.85)
    );

    public static MLPrediction lowRiskPrediction() {
        return LOW_RISK_PREDICTION;
    }

    public static MLPrediction mediumRiskPrediction() {
        return MEDIUM_RISK_PREDICTION;
    }

    public static MLPrediction highRiskPrediction() {
        return HIGH_RISK_PREDICTION;
    }

    public static MLPrediction criticalRiskPrediction() {
        return CRITICAL_RISK_PREDICTION;
    }

    // ========================================
    // Command Builders (Thread-safe)
    // ========================================

    public static AssessTransactionRiskCommand lowRiskCommand(TransactionId transactionId) {
        return lowRiskCommand(transactionId, Instant.now());
    }

    public static AssessTransactionRiskCommand lowRiskCommand(
            TransactionId transactionId,
            Instant timestamp
    ) {
        return AssessTransactionRiskCommand.builder()
                .transactionId(transactionId.toUUID())
                .accountId("ACC-001")
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .type("PURCHASE")
                .channel("MOBILE")
                .merchantId("MER-001")
                .merchantName("Safe Store")
                .merchantCategory("RETAIL")
                .transactionTimestamp(timestamp)
                .location(new LocationDto(-25.7479, 28.2293, "South Africa", "Pretoria"))
                .build();
    }

    public static AssessTransactionRiskCommand mediumRiskCommand(TransactionId transactionId) {
        return mediumRiskCommand(transactionId, Instant.now());
    }

    public static AssessTransactionRiskCommand mediumRiskCommand(
            TransactionId transactionId,
            Instant timestamp
    ) {
        return AssessTransactionRiskCommand.builder()
                .transactionId(transactionId.toUUID())
                .accountId("ACC-002")
                .amount(new BigDecimal("10000.01"))
                .currency("USD")
                .type("PURCHASE")
                .channel("ONLINE")
                .merchantId("MER-002")
                .merchantName("Electronics Store")
                .merchantCategory("ELECTRONICS")
                .transactionTimestamp(timestamp)
                .location(new LocationDto(-25.7479, 28.2293, "South Africa", "Pretoria"))
                .build();
    }

    public static AssessTransactionRiskCommand highRiskCommand(TransactionId transactionId) {
        return highRiskCommand(transactionId, Instant.now());
    }

    public static AssessTransactionRiskCommand highRiskCommand(
            TransactionId transactionId,
            Instant timestamp
    ) {
        return AssessTransactionRiskCommand.builder()
                .transactionId(transactionId.toUUID())
                .accountId("ACC-003")
                .amount(new BigDecimal("50000.01"))
                .currency("USD")
                .type("TRANSFER")
                .channel("ONLINE")
                .merchantId("MER-003")
                .merchantName("Unknown Vendor")
                .merchantCategory("OTHER")
                .transactionTimestamp(timestamp)
                .location(new LocationDto(-25.7479, 28.2293, "South Africa", "Pretoria"))
                .build();
    }

    public static AssessTransactionRiskCommand criticalRiskCommand(TransactionId transactionId) {
        return criticalRiskCommand(transactionId, Instant.now());
    }

    public static AssessTransactionRiskCommand criticalRiskCommand(
            TransactionId transactionId,
            Instant timestamp
    ) {
        return AssessTransactionRiskCommand.builder()
                .transactionId(transactionId.toUUID())
                .accountId("ACC-004")
                .amount(new BigDecimal("100000.01"))
                .currency("USD")
                .type("TRANSFER")
                .channel("ONLINE")
                .merchantId("MER-UNKNOWN")
                .merchantName("Suspicious Vendor")
                .merchantCategory("HIGH_RISK")
                .transactionTimestamp(timestamp)
                .location(new LocationDto(-25.7479, 28.2293, "South Africa", "Pretoria"))
                .build();
    }

    public static AssessTransactionRiskCommand commandForAccount(
            String accountId,
            TransactionId transactionId
    ) {
        return AssessTransactionRiskCommand.builder()
                .transactionId(transactionId.toUUID())
                .accountId(accountId)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .type("PURCHASE")
                .channel("MOBILE")
                .merchantId("MER-VEL")
                .merchantName("Test Merchant")
                .merchantCategory("RETAIL")
                .transactionTimestamp(Instant.now())
                .location(new LocationDto(-25.7479, 28.2293, "South Africa", "Pretoria"))
                .build();
    }
}