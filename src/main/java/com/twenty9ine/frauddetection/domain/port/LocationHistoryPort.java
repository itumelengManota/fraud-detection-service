package com.twenty9ine.frauddetection.domain.port;

import com.twenty9ine.frauddetection.domain.model.Location;

import java.time.Instant;
import java.util.Optional;

public interface LocationHistoryPort {
    Optional<Location> findMostRecent(String accountId, Instant before);
    void save(String accountId, Location location);
}
