package com.twenty9ine.frauddetection.infrastructure.config;

import com.twenty9ine.frauddetection.infrastructure.adapter.rest.MLInferenceClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class MLServiceConfig {

    @Value("${fraud-detection.ml.service.url}")
    private String mlServiceUrl;

    @Bean
    public MLInferenceClient mlInferenceClient(RestClient.Builder builder) {
        RestClient restClient = builder
            .baseUrl(mlServiceUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .requestFactory(new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(50))
                    .build()
            ))
            .build();

        HttpServiceProxyFactory factory = HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build();

        return factory.createClient(MLInferenceClient.class);
    }
}
