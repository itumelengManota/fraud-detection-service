package com.twenty9ine.frauddetection.domain.valueobject;

public enum RuleType {
    VELOCITY,
    GEOGRAPHIC,
    AMOUNT,
    MERCHANT,  //TODO: Add the Drools rule for MERCHANT
    DEVICE //TODO: Add the Drools rule for DEVICE
}
