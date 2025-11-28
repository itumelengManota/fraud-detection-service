package com.twenty9ine.frauddetection.domain.valueobject;

import java.util.Map;

public record MLPrediction(
    String modelId,
    String modelVersion,
    double fraudProbability, //TODO: constrain between 0 and 1
    double confidence, //TODO: constrain between 0 and 1
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
