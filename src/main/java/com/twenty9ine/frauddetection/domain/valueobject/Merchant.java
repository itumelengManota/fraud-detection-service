package com.twenty9ine.frauddetection.domain.valueobject;

import lombok.Builder;

import java.time.Instant;

@Builder
public record Merchant(MerchantId id, String name, String category) {
}
