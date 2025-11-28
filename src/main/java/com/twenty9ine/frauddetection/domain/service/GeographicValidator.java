package com.twenty9ine.frauddetection.domain.service;

import com.twenty9ine.frauddetection.domain.valueobject.GeographicContext;
import com.twenty9ine.frauddetection.domain.valueobject.Location;
import com.twenty9ine.frauddetection.domain.valueobject.Transaction;
import com.twenty9ine.frauddetection.domain.port.LocationHistoryPort;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Optional;

@Slf4j
public class GeographicValidator {

    private static final double IMPOSSIBLE_TRAVEL_THRESHOLD_KMH = 900.0;

    private final LocationHistoryPort locationHistoryPort;

    public GeographicValidator(LocationHistoryPort locationHistoryPort) {
        this.locationHistoryPort = locationHistoryPort;
    }

    public GeographicContext validate(Transaction transaction) {
        Location currentLocation = transaction.location();
        String accountId = transaction.accountId();

        Optional<Location> previousLocation =
            locationHistoryPort.findMostRecent(accountId, transaction.timestamp());

        if (previousLocation.isEmpty()) {
            return GeographicContext.normal();
        }

        double distanceKm = currentLocation.distanceFrom(previousLocation.get());
        Duration timeBetween = Duration.between(
            previousLocation.get().timestamp(),
            transaction.timestamp()
        );

        double requiredSpeedKmh = distanceKm / (timeBetween.toMinutes() / 60.0);

        boolean impossibleTravel = requiredSpeedKmh > IMPOSSIBLE_TRAVEL_THRESHOLD_KMH;

        if (impossibleTravel) {
            log.warn("Impossible travel detected for account {}: {}km in {} minutes ({}km/h)",
                    accountId, distanceKm, timeBetween.toMinutes(), requiredSpeedKmh);
        }

        return GeographicContext.builder()
            .impossibleTravel(impossibleTravel)
            .distanceKm(distanceKm)
            .travelSpeed(requiredSpeedKmh)
            .previousLocation(previousLocation.get())
            .currentLocation(currentLocation)
            .build();
    }
}
