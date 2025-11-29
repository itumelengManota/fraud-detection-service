package com.twenty9ine.frauddetection.application.port.out;

import com.twenty9ine.frauddetection.domain.valueobject.Transaction;
import com.twenty9ine.frauddetection.domain.valueobject.VelocityMetrics;

public interface VelocityServicePort {
    VelocityMetrics findVelocityMetricsByTransaction(Transaction transaction);
    void incrementCounters(Transaction transaction);
}
