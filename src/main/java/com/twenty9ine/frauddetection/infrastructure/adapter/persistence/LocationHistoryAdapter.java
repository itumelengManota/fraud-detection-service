package com.twenty9ine.frauddetection.infrastructure.adapter.persistence;

import com.twenty9ine.frauddetection.domain.valueobject.Location;
import com.twenty9ine.frauddetection.application.port.LocationHistoryPort;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

@Component
public class LocationHistoryAdapter implements LocationHistoryPort {

    @Override
    public Optional<Location> findMostRecent(String accountId, Instant before) {
        return Optional.empty();
    }

    @Override
    public void save(String accountId, Location location) {
    }
}
