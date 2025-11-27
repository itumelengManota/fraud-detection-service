package com.twenty9ine.frauddetection.domain.port;

import com.twenty9ine.frauddetection.domain.model.Location;
import com.twenty9ine.frauddetection.domain.model.VelocityMetrics;

public interface VelocityServicePort {
    VelocityMetrics getVelocity(String accountId);
    void incrementCounters(String accountId, Location location);
}
