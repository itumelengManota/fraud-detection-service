package com.twenty9ine.frauddetection.infrastructure.adapter.rest.dto;
import lombok.Builder;
import java.time.Instant;
import java.util.List;
@Builder
public record ErrorResponse(
String code,
String message,
List<String> details,
Instant timestamp
) {}
