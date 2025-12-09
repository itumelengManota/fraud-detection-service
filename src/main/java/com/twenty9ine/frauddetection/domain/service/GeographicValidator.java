package com.twenty9ine.frauddetection.domain.service;

import com.twenty9ine.frauddetection.application.port.out.TransactionRepository;
import com.twenty9ine.frauddetection.domain.valueobject.GeographicContext;
import com.twenty9ine.frauddetection.domain.valueobject.Location;
import com.twenty9ine.frauddetection.domain.valueobject.Transaction;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Optional;

@Slf4j
public final class GeographicValidator {

    private static final double IMPOSSIBLE_TRAVEL_THRESHOLD_KMH = 965.0; //average cruising speed for a commercial jet

    private final TransactionRepository transactionRepository;

    public GeographicValidator(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public GeographicContext validate(Transaction transaction) {
        Location currentLocation = transaction.location();
        String accountId = transaction.accountId();

        Optional<Location> optionalPreviousLocation = findPreviousLocationByAccountId(accountId);

        if (optionalPreviousLocation.isEmpty()) {
            return GeographicContext.normal();
        }

        Location previousLocation = optionalPreviousLocation.get();
        double displacementKm = calculateDisplacement(currentLocation, previousLocation);
        Duration durationBetween = calculateBetweenDuration(transaction, previousLocation);

        double requiredSpeedKmh = calculateRequiredSpeed(displacementKm, durationBetween);
        boolean impossibleTravel = isImpossibleTravel(requiredSpeedKmh);

        if (impossibleTravel) {
            log.warn("Impossible travel detected for account {}: {}km in {} minutes ({}km/h)",
                    accountId, displacementKm, durationBetween.toMinutes(), requiredSpeedKmh);
        }

        return GeographicContext.builder()
                .isImpossibleTravel(impossibleTravel)
                .distanceKm(displacementKm)
                .travelSpeed(requiredSpeedKmh)
                .previousLocation(previousLocation)
                .currentLocation(currentLocation)
                .build();
    }

    private static boolean isImpossibleTravel(double requiredSpeedKmh) {
        return requiredSpeedKmh > IMPOSSIBLE_TRAVEL_THRESHOLD_KMH;
    }

    private static double calculateRequiredSpeed(double displacementKm, Duration durationBetween) {
        double hours = durationBetween.toMinutes() / 60.0;
        return displacementKm / hours;
    }

    private static double calculateDisplacement(Location currentLocation, Location previousLocation) {
        return currentLocation.distanceFrom(previousLocation);
    }

    private static Duration calculateBetweenDuration(Transaction transaction, Location location) {
        return Duration.between(location.timestamp(), transaction.timestamp());
    }

    private Optional<Location> findPreviousLocationByAccountId(String accountId) {
        return transactionRepository.findEarliestByAccountId(accountId)
                .map(Transaction::location);
    }
}
