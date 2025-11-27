package com.twenty9ine.frauddetection.infrastructure.adapter.rest.dto;

import java.util.Map;

public record MLPredictionResponse(
    String modelId,
    String modelVersion,
    double fraudProbability,
    double confidence,
    Map<String, Double> featureImportance
) {}
