package com.twenty9ine.frauddetection.infrastructure.adapter.persistence.mapper;

import com.twenty9ine.frauddetection.domain.valueobject.Location;
import com.twenty9ine.frauddetection.infrastructure.adapter.persistence.entity.LocationEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class LocationMapperTest {

    private LocationMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = Mappers.getMapper(LocationMapper.class);
    }

    @Test
    void testToEntity_CompleteLocation_MapsAllFields() {
        Instant timestamp = Instant.now();
        Location location = Location.of(40.7128, -74.0060, "USA", "New York", timestamp);

        LocationEntity entity = mapper.toEntity(location);

        assertNotNull(entity);
        assertNull(entity.getId()); // ID should be ignored
        assertEquals(40.7128, entity.getLatitude());
        assertEquals(-74.0060, entity.getLongitude());
        assertEquals("USA", entity.getCountry());
        assertEquals("New York", entity.getCity());
        assertEquals(timestamp, entity.getTimestamp());
    }

    @Test
    void testToEntity_MinimalLocation_MapsRequiredFields() {
        Instant timestamp = Instant.now();
        Location location = Location.of(51.5074, -0.1278, timestamp);

        LocationEntity entity = mapper.toEntity(location);

        assertNotNull(entity);
        assertEquals(51.5074, entity.getLatitude());
        assertEquals(-0.1278, entity.getLongitude());
        assertNull(entity.getCountry());
        assertNull(entity.getCity());
        assertEquals(timestamp, entity.getTimestamp());
    }

    @Test
    void testToEntity_LocationWithoutCountryAndCity_MapsCorrectly() {
        Instant timestamp = Instant.now();
        Location location = new Location(48.8566, 2.3522, null, null, timestamp);

        LocationEntity entity = mapper.toEntity(location);

        assertNotNull(entity);
        assertEquals(48.8566, entity.getLatitude());
        assertEquals(2.3522, entity.getLongitude());
        assertNull(entity.getCountry());
        assertNull(entity.getCity());
        assertEquals(timestamp, entity.getTimestamp());
    }

    @Test
    void testToEntity_NegativeCoordinates_MapsCorrectly() {
        Instant timestamp = Instant.now();
        Location location = Location.of(-33.8688, -151.2093, "Australia", "Sydney", timestamp);

        LocationEntity entity = mapper.toEntity(location);

        assertEquals(-33.8688, entity.getLatitude());
        assertEquals(-151.2093, entity.getLongitude());
        assertEquals("Australia", entity.getCountry());
        assertEquals("Sydney", entity.getCity());
    }

    @Test
    void testToEntity_ZeroCoordinates_MapsCorrectly() {
        Instant timestamp = Instant.now();
        Location location = Location.of(0.0, 0.0, "Ghana", "Null Island", timestamp);

        LocationEntity entity = mapper.toEntity(location);

        assertEquals(0.0, entity.getLatitude());
        assertEquals(0.0, entity.getLongitude());
        assertEquals("Ghana", entity.getCountry());
        assertEquals("Null Island", entity.getCity());
    }

    @Test
    void testToEntity_NullLocation_ReturnsNull() {
        LocationEntity entity = mapper.toEntity(null);

        assertNull(entity);
    }

    @Test
    void testToDomain_CompleteEntity_MapsAllFields() {
        Instant timestamp = Instant.now();

        LocationEntity entity = new LocationEntity();
        entity.setLatitude(35.6762);
        entity.setLongitude(139.6503);
        entity.setCountry("Japan");
        entity.setCity("Tokyo");
        entity.setTimestamp(timestamp);

        Location location = mapper.toDomain(entity);

        assertNotNull(location);
        assertEquals(35.6762, location.latitude());
        assertEquals(139.6503, location.longitude());
        assertEquals("Japan", location.country());
        assertEquals("Tokyo", location.city());
        assertEquals(timestamp, location.timestamp());
    }

    @Test
    void testToDomain_MinimalEntity_MapsRequiredFields() {
        Instant timestamp = Instant.now();

        LocationEntity entity = new LocationEntity();
        entity.setLatitude(55.7558);
        entity.setLongitude(37.6173);
        entity.setCountry(null);
        entity.setCity(null);
        entity.setTimestamp(timestamp);

        Location location = mapper.toDomain(entity);

        assertNotNull(location);
        assertEquals(55.7558, location.latitude());
        assertEquals(37.6173, location.longitude());
        assertNull(location.country());
        assertNull(location.city());
        assertEquals(timestamp, location.timestamp());
    }

    @Test
    void testToDomain_NegativeCoordinates_MapsCorrectly() {
        Instant timestamp = Instant.now();

        LocationEntity entity = new LocationEntity();
        entity.setLatitude(-34.6037);
        entity.setLongitude(-58.3816);
        entity.setCountry("Argentina");
        entity.setCity("Buenos Aires");
        entity.setTimestamp(timestamp);

        Location location = mapper.toDomain(entity);

        assertEquals(-34.6037, location.latitude());
        assertEquals(-58.3816, location.longitude());
        assertEquals("Argentina", location.country());
        assertEquals("Buenos Aires", location.city());
    }

    @Test
    void testToDomain_ZeroCoordinates_MapsCorrectly() {
        Instant timestamp = Instant.now();

        LocationEntity entity = new LocationEntity();
        entity.setLatitude(0.0);
        entity.setLongitude(0.0);
        entity.setCountry("Atlantic Ocean");
        entity.setCity("Null Island");
        entity.setTimestamp(timestamp);

        Location location = mapper.toDomain(entity);

        assertEquals(0.0, location.latitude());
        assertEquals(0.0, location.longitude());
        assertEquals("Atlantic Ocean", location.country());
        assertEquals("Null Island", location.city());
    }

    @Test
    void testToDomain_NullEntity_ReturnsNull() {
        Location location = mapper.toDomain(null);

        assertNull(location);
    }

    @Test
    void testRoundTrip_DomainToEntityToDomain_PreservesData() {
        Instant timestamp = Instant.now();
        Location originalLocation = Location.of(52.5200, 13.4050, "Germany", "Berlin", timestamp);

        LocationEntity entity = mapper.toEntity(originalLocation);
        Location roundTripLocation = mapper.toDomain(entity);

        assertEquals(originalLocation.latitude(), roundTripLocation.latitude());
        assertEquals(originalLocation.longitude(), roundTripLocation.longitude());
        assertEquals(originalLocation.country(), roundTripLocation.country());
        assertEquals(originalLocation.city(), roundTripLocation.city());
        assertEquals(originalLocation.timestamp(), roundTripLocation.timestamp());
    }

    @Test
    void testRoundTrip_EntityToDomainToEntity_PreservesData() {
        Instant timestamp = Instant.now();

        LocationEntity originalEntity = new LocationEntity();
        originalEntity.setLatitude(41.9028);
        originalEntity.setLongitude(12.4964);
        originalEntity.setCountry("Italy");
        originalEntity.setCity("Rome");
        originalEntity.setTimestamp(timestamp);

        Location location = mapper.toDomain(originalEntity);
        LocationEntity roundTripEntity = mapper.toEntity(location);

        assertEquals(originalEntity.getLatitude(), roundTripEntity.getLatitude());
        assertEquals(originalEntity.getLongitude(), roundTripEntity.getLongitude());
        assertEquals(originalEntity.getCountry(), roundTripEntity.getCountry());
        assertEquals(originalEntity.getCity(), roundTripEntity.getCity());
        assertEquals(originalEntity.getTimestamp(), roundTripEntity.getTimestamp());
    }

    @Test
    void testRoundTrip_WithNullOptionalFields_PreservesData() {
        Instant timestamp = Instant.now();
        Location originalLocation = Location.of(59.3293, 18.0686, timestamp);

        LocationEntity entity = mapper.toEntity(originalLocation);
        Location roundTripLocation = mapper.toDomain(entity);

        assertEquals(originalLocation.latitude(), roundTripLocation.latitude());
        assertEquals(originalLocation.longitude(), roundTripLocation.longitude());
        assertNull(roundTripLocation.country());
        assertNull(roundTripLocation.city());
        assertEquals(originalLocation.timestamp(), roundTripLocation.timestamp());
    }

    @Test
    void testToEntity_ExtremeCoordinates_MapsCorrectly() {
        Instant timestamp = Instant.now();
        Location location = Location.of(90.0, 180.0, "North Pole", "Arctic", timestamp);

        LocationEntity entity = mapper.toEntity(location);

        assertEquals(90.0, entity.getLatitude());
        assertEquals(180.0, entity.getLongitude());
        assertEquals("North Pole", entity.getCountry());
        assertEquals("Arctic", entity.getCity());
    }

    @Test
    void testToDomain_ExtremeCoordinates_MapsCorrectly() {
        Instant timestamp = Instant.now();

        LocationEntity entity = new LocationEntity();
        entity.setLatitude(-90.0);
        entity.setLongitude(-180.0);
        entity.setCountry("Antarctica");
        entity.setCity("South Pole");
        entity.setTimestamp(timestamp);

        Location location = mapper.toDomain(entity);

        assertEquals(-90.0, location.latitude());
        assertEquals(-180.0, location.longitude());
        assertEquals("Antarctica", location.country());
        assertEquals("South Pole", location.city());
    }

    @Test
    void testToEntity_PrecisionPreservation_MaintainsAccuracy() {
        Instant timestamp = Instant.now();
        Location location = Location.of(40.748817, -73.985428, "USA", "NYC", timestamp);

        LocationEntity entity = mapper.toEntity(location);

        assertEquals(40.748817, entity.getLatitude(), 0.000001);
        assertEquals(-73.985428, entity.getLongitude(), 0.000001);
    }

    @Test
    void testToDomain_PrecisionPreservation_MaintainsAccuracy() {
        Instant timestamp = Instant.now();

        LocationEntity entity = new LocationEntity();
        entity.setLatitude(51.507351);
        entity.setLongitude(-0.127758);
        entity.setCountry("UK");
        entity.setCity("London");
        entity.setTimestamp(timestamp);

        Location location = mapper.toDomain(entity);

        assertEquals(51.507351, location.latitude(), 0.000001);
        assertEquals(-0.127758, location.longitude(), 0.000001);
    }
}