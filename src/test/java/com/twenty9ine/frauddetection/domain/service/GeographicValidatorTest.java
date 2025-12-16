package com.twenty9ine.frauddetection.domain.service;

import com.twenty9ine.frauddetection.application.port.out.TransactionRepository;
import com.twenty9ine.frauddetection.domain.valueobject.GeographicContext;
import com.twenty9ine.frauddetection.domain.valueobject.Location;
import com.twenty9ine.frauddetection.domain.valueobject.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GeographicValidatorTest {

    @Mock
    private TransactionRepository transactionRepository;

    private GeographicValidator validator;

    @BeforeEach
    void setUp() {
        validator = new GeographicValidator(transactionRepository);
    }

    @Test
    void validate_shouldReturnNormalContext_whenNoPreviousTransaction() {
        // Given
        Transaction transaction = createTransaction("ACC123", Location.of(0.0, 0.0, Instant.now()));
        when(transactionRepository.findEarliestByAccountId("ACC123")).thenReturn(Optional.empty());

        // When
        GeographicContext result = validator.validate(transaction);

        // Then
        assertFalse(result.isImpossibleTravel());
        assertEquals(0.0, result.distanceKm());
        assertEquals(0.0, result.travelSpeed());
        assertNull(result.previousLocation());
        assertNull(result.currentLocation());
    }

    @Test
    void validate_shouldDetectImpossibleTravel_whenSpeedExceedsThreshold() {
        // Given
        Instant now = Instant.now();
        Location previousLocation = Location.of(40.7128, -74.0060, now.minusSeconds(3600)); // New York
        Location currentLocation = Location.of(51.5074, -0.1278, now); // London (~5570km apart)

        Transaction previousTransaction = createTransaction("ACC123", previousLocation);
        Transaction currentTransaction = createTransaction("ACC123", currentLocation);

        when(transactionRepository.findEarliestByAccountId("ACC123"))
                .thenReturn(Optional.of(previousTransaction));

        // When
        GeographicContext result = validator.validate(currentTransaction);

        // Then
        assertTrue(result.isImpossibleTravel());
        assertTrue(result.distanceKm() > 5000);
        assertTrue(result.travelSpeed() > 965.0);
        assertEquals(previousLocation, result.previousLocation());
        assertEquals(currentLocation, result.currentLocation());
    }

    @Test
    void validate_shouldNotDetectImpossibleTravel_whenSpeedIsReasonable() {
        // Given
        Instant now = Instant.now();
        Location previousLocation = Location.of(40.7128, -74.0060, now.minusSeconds(36000)); // 10 hours ago
        Location currentLocation = Location.of(34.0522, -118.2437, now); // Los Angeles (~3944km)

        Transaction previousTransaction = createTransaction("ACC123", previousLocation);
        Transaction currentTransaction = createTransaction("ACC123", currentLocation);

        when(transactionRepository.findEarliestByAccountId("ACC123"))
                .thenReturn(Optional.of(previousTransaction));

        // When
        GeographicContext result = validator.validate(currentTransaction);

        // Then
        assertFalse(result.isImpossibleTravel());
        assertTrue(result.distanceKm() > 3000);
        assertTrue(result.travelSpeed() < 965.0);
    }

    @Test
    void validate_shouldHandleSameLocation() {
        // Given
        Instant now = Instant.now();
        Location location = Location.of(40.7128, -74.0060, now.minusSeconds(3600));

        Transaction previousTransaction = createTransaction("ACC123", location);
        Transaction currentTransaction = createTransaction("ACC123", Location.of(40.7128, -74.0060, now));

        when(transactionRepository.findEarliestByAccountId("ACC123"))
                .thenReturn(Optional.of(previousTransaction));

        // When
        GeographicContext result = validator.validate(currentTransaction);

        // Then
        assertFalse(result.isImpossibleTravel());
        assertEquals(0.0, result.distanceKm(), 0.01);
        assertEquals(0.0, result.travelSpeed());
    }

    @Test
    void isImpossibleTravel_shouldReturnTrue_whenSpeedExceedsThreshold() throws Exception {
        // Given
        Method method = GeographicValidator.class.getDeclaredMethod("isImpossibleTravel", double.class);
        method.setAccessible(true);

        // When
        boolean result = (boolean) method.invoke(null, 1000.0);

        // Then
        assertTrue(result);
    }

    @Test
    void isImpossibleTravel_shouldReturnFalse_whenSpeedBelowThreshold() throws Exception {
        // Given
        Method method = GeographicValidator.class.getDeclaredMethod("isImpossibleTravel", double.class);
        method.setAccessible(true);

        // When
        boolean result = (boolean) method.invoke(null, 900.0);

        // Then
        assertFalse(result);
    }

    @Test
    void calculateRequiredSpeed_shouldCalculateCorrectly() throws Exception {
        // Given
        Method method = GeographicValidator.class.getDeclaredMethod("calculateRequiredSpeed", double.class, Duration.class);
        method.setAccessible(true);

        // When
        double result = (double) method.invoke(null, 500.0, Duration.ofHours(1));

        // Then
        assertEquals(500.0, result);
    }

    @Test
    void calculateRequiredSpeed_shouldHandleMinutes() throws Exception {
        // Given
        Method method = GeographicValidator.class.getDeclaredMethod("calculateRequiredSpeed", double.class, Duration.class);
        method.setAccessible(true);

        // When
        double result = (double) method.invoke(null, 100.0, Duration.ofMinutes(30));

        // Then
        assertEquals(200.0, result);
    }

    @Test
    void calculateDisplacement_shouldReturnDistance() throws Exception {
        // Given
        Method method = GeographicValidator.class.getDeclaredMethod("calculateDisplacement", Location.class, Location.class);
        method.setAccessible(true);
        Location loc1 = Location.of(40.7128, -74.0060);
        Location loc2 = Location.of(34.0522, -118.2437);

        // When
        double result = (double) method.invoke(null, loc1, loc2);

        // Then
        assertTrue(result > 3900 && result < 4000);
    }

    @Test
    void calculateBetweenDuration_shouldReturnCorrectDuration() throws Exception {
        // Given
        Method method = GeographicValidator.class.getDeclaredMethod("calculateBetweenDuration", Transaction.class, Location.class);
        method.setAccessible(true);
        Instant now = Instant.now();
        Location location = Location.of(0.0, 0.0, now.minusSeconds(3600));
        Transaction transaction = createTransaction("ACC123", Location.of(0.0, 0.0, now));

        // When
        Duration result = (Duration) method.invoke(null, transaction, location);

        // Then
        assertEquals(3600, result.getSeconds());
    }

//    @Test
//    void findPreviousLocationByAccountId_shouldReturnLocation_whenTransactionExists() throws Exception {
//        // Given
//        Method method = GeographicValidator.class.getDeclaredMethod("findPreviousLocationByAccountId", String.class);
//        method.setAccessible(true);
//
//        Location location = Location.of(40.7128, -74.0060);
//        Transaction transaction = createTransaction("ACC123", location);
////        when(transactionRepository.findEarliestByAccountId("ACC123")).thenReturn(Optional.of(transaction));
//
//        // When
//        @SuppressWarnings("unchecked")
//        Optional<Location> result = (Optional<Location>) method.invoke(validator, "ACC123");
//
//        // Then
//        assertTrue(result.isPresent());
//        assertEquals(location, result.get());
//    }

    @Test
    void findPreviousLocationByAccountId_shouldReturnEmpty_whenNoTransaction() throws Exception {
        // Given
        Method method = GeographicValidator.class.getDeclaredMethod("findPreviousLocationByAccountId", String.class);
        method.setAccessible(true);
        when(transactionRepository.findEarliestByAccountId("ACC123")).thenReturn(Optional.empty());

        // When
        @SuppressWarnings("unchecked")
        Optional<Location> result = (Optional<Location>) method.invoke(validator, "ACC123");

        // Then
        assertFalse(result.isPresent());
    }

    private Transaction createTransaction(String accountId, Location location) {
        return Transaction.builder()
                .accountId(accountId)
                .location(location)
                .timestamp(location.timestamp())
                .build();
    }
}