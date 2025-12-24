package com.twenty9ine.frauddetection.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sagemakerfeaturestoreruntime.SageMakerFeatureStoreRuntimeClient;
import software.amazon.awssdk.services.sagemakerruntime.SageMakerRuntimeClient;

import java.net.URI;
import java.time.Duration;

@Configuration
@ConditionalOnProperty(name = "aws.sagemaker.enabled", havingValue = "true")
public class SageMakerConfig {

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    @Value("${aws.sagemaker.endpoint-url:http://localhost:8080/invocations}")
    private String awsEndpointUrl;

    @Value("${aws.sagemaker.api-call-timeout:2s}")
    private Duration apiCallTimeout;

    @Value("${aws.sagemaker.api-call-attempt-timeout:1s}")
    private Duration apiCallAttemptTimeout;

    @Profile("!dev")
    @Bean
    public SageMakerRuntimeClient sageMakerRuntimeClient() {
        return SageMakerRuntimeClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .overrideConfiguration(config -> config
                        .apiCallTimeout(apiCallTimeout)
                        .apiCallAttemptTimeout(apiCallAttemptTimeout))
                .build();
    }

    @Profile("dev")
    @Bean
    public SageMakerRuntimeClient sageMakerRuntimeClientLocal() {
        return SageMakerRuntimeClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .endpointOverride(URI.create(awsEndpointUrl))
                .overrideConfiguration(config -> config
                        .apiCallTimeout(apiCallTimeout)
                        .apiCallAttemptTimeout(apiCallAttemptTimeout))
                .build();
    }

    @Bean
    @Profile("!test")
    @ConditionalOnProperty(name = "aws.sagemaker.feature-store.enabled", havingValue = "true")
    public SageMakerFeatureStoreRuntimeClient sageMakerFeatureStoreRuntimeClient() {
        return SageMakerFeatureStoreRuntimeClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}