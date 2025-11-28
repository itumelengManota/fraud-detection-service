package com.twenty9ine.frauddetection.domain.port;

import com.twenty9ine.frauddetection.domain.valueobject.Location;
import com.twenty9ine.frauddetection.domain.valueobject.VelocityMetrics;

public interface VelocityServicePort {
    VelocityMetrics getVelocity(String accountId);
    void incrementCounters(String accountId, Location location);
}
