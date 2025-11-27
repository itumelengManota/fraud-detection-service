package com.twenty9ine.frauddetection.infrastructure.adapter.rest;

import com.twenty9ine.frauddetection.infrastructure.adapter.rest.dto.FeatureVector;
import com.twenty9ine.frauddetection.infrastructure.adapter.rest.dto.MLPredictionResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.PostExchange;

public interface MLInferenceClient {

    @PostExchange("/api/v1/predict")
    MLPredictionResponse predict(@RequestBody FeatureVector features);

    @GetExchange("/health")
    void healthCheck();
}
