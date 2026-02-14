package com.twenty9ine.frauddetection.domain.valueobject;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LocationTest {

    @Test
    void testDistanceFrom_SameLocation_ReturnsZero() {
        Location location = new Location(40.7128, -74.0060, "USA", "New York");

        double distance = location.distanceFrom(location);

        assertEquals(0.0, distance, 0.01);
    }

    @Test
    void testDistanceFrom_NewYorkToLondon_ReturnsAccurateDistance() {
        Location newYork = new Location(40.7128, -74.0060, "USA", "New York");
        Location london = new Location(51.5074, -0.1278, "UK", "London");

        double distance = newYork.distanceFrom(london);

        // Actual distance is approximately 5570 km
        assertEquals(5570.0, distance, 50.0);
    }

    @Test
    void testDistanceFrom_SydneyToTokyo_ReturnsAccurateDistance() {
        Location sydney = new Location(-33.8688, 151.2093, "Australia", "Sydney");
        Location tokyo = new Location(35.6762, 139.6503, "Japan", "Tokyo");

        double distance = sydney.distanceFrom(tokyo);

        // Actual distance is approximately 7820 km
        assertEquals(7820.0, distance, 50.0);
    }

    @Test
    void testDistanceFrom_ParisToRome_ReturnsAccurateDistance() {
        Location paris = new Location(48.8566, 2.3522, "France", "Paris");
        Location rome = new Location(41.9028, 12.4964, "Italy", "Rome");

        double distance = paris.distanceFrom(rome);

        // Actual distance is approximately 1105 km
        assertEquals(1105.0, distance, 20.0);
    }

    @Test
    void testDistanceFrom_ShortDistance_ReturnsAccurateDistance() {
        Location location1 = new Location(40.7128, -74.0060, "USA", "New York");
        Location location2 = new Location(40.7489, -73.9680, "USA", "Queens");

        double distance = location1.distanceFrom(location2);

        // Distance is approximately 5.13 km
        assertEquals(5.13, distance, 0.5);
    }

    @Test
    void testDistanceFrom_AntipodePoints_ReturnsHalfEarthCircumference() {
        Location location1 = new Location(0.0, 0.0, "Ecuador", "Quito");
        Location location2 = new Location(0.0, 180.0, "Indonesia", "Jakarta");

        double distance = location1.distanceFrom(location2);

        // Half of Earth's circumference is approximately 20015 km
        assertEquals(20015.0, distance, 100.0);
    }

    @Test
    void testDistanceFrom_NorthPoleToSouthPole_ReturnsHalfEarthCircumference() {
        Location northPole = new Location(90.0, 0.0, null, null);
        Location southPole = new Location(-90.0, 0.0, null, null);

        double distance = northPole.distanceFrom(southPole);

        // Half of Earth's circumference through poles is approximately 20004 km
        assertEquals(20004.0, distance, 100.0);
    }

    @Test
    void testDistanceFrom_IsSymmetric() {
        Location location1 = new Location(40.7128, -74.0060, "USA", "New York");
        Location location2 = new Location(51.5074, -0.1278, "UK", "London");

        double distance1to2 = location1.distanceFrom(location2);
        double distance2to1 = location2.distanceFrom(location1);

        assertEquals(distance1to2, distance2to1, 0.01);
    }
}