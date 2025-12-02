package com.twenty9ine.frauddetection.domain.valueobject;

public record MerchantId(String merchantId) {
    public static MerchantId of(String merchantId) {
        return new MerchantId(merchantId);
    }
}
