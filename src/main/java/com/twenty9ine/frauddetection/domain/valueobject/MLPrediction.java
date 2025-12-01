package com.twenty9ine.frauddetection.domain.valueobject;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.util.Map;

public record MLPrediction(
    String modelId,
    String modelVersion,

    @DecimalMin(value = "0.0", message = "Fraud probability must be between 0.0 and 1.0")
    @DecimalMax(value = "1.0", message = "Fraud probability must be between 0.0 and 1.0")
    double fraudProbability,

    @DecimalMin(value = "0.0", message = "Confidence must be between 0.0 and 1.0")
    @DecimalMax(value = "1.0", message = "Confidence must be between 0.0 and 1.0")
    double confidence,

    Map<String, Double> featureImportance
) {
    public static MLPrediction unavailable() {
        return new MLPrediction(
            "unavailable",
            "0.0.0",
            0.0,
            0.0,
            Map.of()
        );
    }
}