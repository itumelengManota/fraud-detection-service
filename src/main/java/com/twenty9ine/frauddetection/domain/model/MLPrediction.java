package com.twenty9ine.frauddetection.domain.model;

import java.util.Map;

public record MLPrediction(
    String modelId,
    String modelVersion,
    double fraudProbability,
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

    public boolean isAvailable() {
        return !"unavailable".equals(modelId);
    }
}
