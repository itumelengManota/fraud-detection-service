package com.twenty9ine.frauddetection.domain.valueobject;

public enum Channel {
    CARD,
    ACH,
    WIRE,
    MOBILE,
    ONLINE,
    POS,
    ATM;

    public static Channel fromString(String channel) {
        for (Channel c : Channel.values()) {
            if (c.name().equalsIgnoreCase(channel))
                return c;
        }

        throw new IllegalArgumentException("Unknown channel: " + channel);
    }
}
