package com.twenty9ine.frauddetection.infrastructure.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the Logback {@link OpenTelemetryAppender} (registered in
 * {@code logback-spring.xml}) to the application's {@link OpenTelemetry}
 * SDK instance autoconfigured by {@code spring-boot-starter-opentelemetry}.
 *
 * <p>Without this bridge the appender silently drops every log event,
 * because Spring Boot 4's OTel starter does not include the
 * {@code opentelemetry-spring-boot-starter} autoinstrumentation that
 * normally calls {@code OpenTelemetryAppender.install(...)}.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class OpenTelemetryLogBridgeConfig {

    private final OpenTelemetry openTelemetry;

    @PostConstruct
    void installOtelAppender() {
        OpenTelemetryAppender.install(openTelemetry);
        log.info("OpenTelemetry Logback appender installed — log events will be exported via OTLP");
    }
}
