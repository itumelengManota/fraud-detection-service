package com.twenty9ine.frauddetection.domain.valueobject;

public enum MerchantCategory {
    GROCERY,
    RESTAURANT,
    GAS,
    RETAIL,
    ENTERTAINMENT,
    ELECTRONICS,
    JEWELRY,
    CRYPTO,
    GIFT_CARDS,
    GAMBLING;

    public static MerchantCategory fromString(String category) {
        for (MerchantCategory merchantCategory : MerchantCategory.values()) {
            if (merchantCategory.name().equalsIgnoreCase(category))
                return merchantCategory;
        }

        throw new IllegalArgumentException("Unknown MerchantCategory: " + category);
    }
}
