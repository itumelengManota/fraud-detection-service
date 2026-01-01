package com.twenty9ine.frauddetection.domain.valueobject;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.time.Instant;

/**
 * Account holder profile information from Account bounded context.
 * Contains minimal data needed for fraud detection - follows privacy-by-design principle.
 *
 * @author Ignatius Itumeleng Manota
 */
@Builder
public record AccountProfile(@NotNull String accountId, @NotNull Location homeLocation, Instant accountCreatedAt) {
    /**
     * Calculates distance between home location and a given location.
     *
     * @param location the location to measure distance from home
     * @return distance in kilometers
     */
    public double distanceFromHome(Location location) {
        if (location == null) {
            return 0.0;
        }

        return homeLocation.distanceFrom(location);
    }

    /**
     * Checks if transaction location is in the same country as home.
     *
     * @param location the location to check
     * @return true if in home country
     */
    public boolean isInHomeCountry(Location location) {
        if (location == null || location.country() == null || homeLocation.country() == null) {
            return false;
        }
        return homeLocation.country().equalsIgnoreCase(location.country());
    }

    /**
     * Creates a default profile when account data is unavailable.
     * Used as fallback to ensure ML predictions can still occur.
     *
     * @param accountId the account identifier
     * @return default profile with no home location
     */
    public static AccountProfile unavailable(String accountId) {
        return AccountProfile.builder()
                .accountId(accountId)
                .homeLocation(null)
                .accountCreatedAt(null)
                .build();
    }
}