package com.twenty9ine.frauddetection.domain.valueobject;

import lombok.Builder;

@Builder
public record Merchant(MerchantId id, String name, MerchantCategory category) {
}
