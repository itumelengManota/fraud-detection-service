package com.twenty9ine.frauddetection.infrastructure.adapter.persistence.mapper;

import com.twenty9ine.frauddetection.domain.valueobject.Location;
import com.twenty9ine.frauddetection.infrastructure.adapter.persistence.entity.LocationEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.mapstruct.factory.Mappers;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.junit.jupiter.api.Assertions.*;

@Execution(ExecutionMode.CONCURRENT)
class LocationMapperTest {

    private LocationMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = Mappers.getMapper(LocationMapper.class);
    }

    @Test
    void testToEntity_CompleteLocation_MapsAllFields() {
        Location location = Location.of(40.7128, -74.0060, "USA", "New York");

        LocationEntity entity = mapper.toEntity(location);

        assertNotNull(entity);
        assertEquals(40.7128, entity.latitude());
        assertEquals(-74.0060, entity.longitude());
        assertEquals("USA", entity.country());
        assertEquals("New York", entity.city());
    }

    @Test
    void testToEntity_MinimalLocation_MapsRequiredFields() {
        Location location = Location.of(51.5074, -0.1278);

        LocationEntity entity = mapper.toEntity(location);

        assertNotNull(entity);
        assertEquals(51.5074, entity.latitude());
        assertEquals(-0.1278, entity.longitude());
        assertNull(entity.country());
        assertNull(entity.city());
    }

    @Test
    void testToEntity_LocationWithoutCountryAndCity_MapsCorrectly() {
        Location location = new Location(48.8566, 2.3522, null, null);

        LocationEntity entity = mapper.toEntity(location);

        assertNotNull(entity);
        assertEquals(48.8566, entity.latitude());
        assertEquals(2.3522, entity.longitude());
        assertNull(entity.country());
        assertNull(entity.city());
    }

    @Test
    void testToEntity_NegativeCoordinates_MapsCorrectly() {
        Location location = Location.of(-33.8688, -151.2093, "Australia", "Sydney");

        LocationEntity entity = mapper.toEntity(location);

        assertEquals(-33.8688, entity.latitude());
        assertEquals(-151.2093, entity.longitude());
        assertEquals("Australia", entity.country());
        assertEquals("Sydney", entity.city());
    }

    @Test
    void testToEntity_ZeroCoordinates_MapsCorrectly() {
        Location location = Location.of(0.0, 0.0, "Ghana", "Null Island");

        LocationEntity entity = mapper.toEntity(location);

        assertEquals(0.0, entity.latitude());
        assertEquals(0.0, entity.longitude());
        assertEquals("Ghana", entity.country());
        assertEquals("Null Island", entity.city());
    }

    @Test
    void testToEntity_NullLocation_ReturnsNull() {
        LocationEntity entity = mapper.toEntity(null);

        assertNull(entity);
    }

    @Test
    void testToDomain_CompleteEntity_MapsAllFields() {
        LocationEntity entity = LocationEntity.builder()
                .latitude(35.6762)
                .longitude(139.6503)
                .country("Japan")
                .city("Tokyo")
                .build();

        Location location = mapper.toDomain(entity);

        assertNotNull(location);
        assertEquals(35.6762, location.latitude());
        assertEquals(139.6503, location.longitude());
        assertEquals("Japan", location.country());
        assertEquals("Tokyo", location.city());
    }

    @Test
    void testToDomain_MinimalEntity_MapsRequiredFields() {
        LocationEntity entity = LocationEntity.builder()
                .latitude(55.7558)
                .longitude(37.6173)
                .country(null)
                .city(null)
                .build();

        Location location = mapper.toDomain(entity);

        assertNotNull(location);
        assertEquals(55.7558, location.latitude());
        assertEquals(37.6173, location.longitude());
        assertNull(location.country());
        assertNull(location.city());
    }

    @Test
    void testToDomain_NegativeCoordinates_MapsCorrectly() {
        LocationEntity entity = LocationEntity.builder()
                .latitude(-34.6037)
                .longitude(-58.3816)
                .country("Argentina")
                .city("Buenos Aires")
                .build();

        Location location = mapper.toDomain(entity);

        assertEquals(-34.6037, location.latitude());
        assertEquals(-58.3816, location.longitude());
        assertEquals("Argentina", location.country());
        assertEquals("Buenos Aires", location.city());
    }

    @Test
    void testToDomain_ZeroCoordinates_MapsCorrectly() {
        LocationEntity entity = LocationEntity.builder()
                .latitude(0.0)
                .longitude(0.0)
                .country("Atlantic Ocean")
                .city("Null Island")
                .build();

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
        Location originalLocation = Location.of(52.5200, 13.4050, "Germany", "Berlin");

        LocationEntity entity = mapper.toEntity(originalLocation);
        Location roundTripLocation = mapper.toDomain(entity);

        assertEquals(originalLocation.latitude(), roundTripLocation.latitude());
        assertEquals(originalLocation.longitude(), roundTripLocation.longitude());
        assertEquals(originalLocation.country(), roundTripLocation.country());
        assertEquals(originalLocation.city(), roundTripLocation.city());
    }

    @Test
    void testRoundTrip_EntityToDomainToEntity_PreservesData() {
        LocationEntity originalEntity = LocationEntity.builder()
                .latitude(41.9028)
                .longitude(12.4964)
                .country("Italy")
                .city("Rome")
                .build();

        Location location = mapper.toDomain(originalEntity);
        LocationEntity roundTripEntity = mapper.toEntity(location);

        assertEquals(originalEntity.latitude(), roundTripEntity.latitude());
        assertEquals(originalEntity.longitude(), roundTripEntity.longitude());
        assertEquals(originalEntity.country(), roundTripEntity.country());
        assertEquals(originalEntity.city(), roundTripEntity.city());
    }

    @Test
    void testRoundTrip_WithNullOptionalFields_PreservesData() {
        Location originalLocation = Location.of(59.3293, 18.0686);

        LocationEntity entity = mapper.toEntity(originalLocation);
        Location roundTripLocation = mapper.toDomain(entity);

        assertEquals(originalLocation.latitude(), roundTripLocation.latitude());
        assertEquals(originalLocation.longitude(), roundTripLocation.longitude());
        assertNull(roundTripLocation.country());
        assertNull(roundTripLocation.city());
    }

    @Test
    void testToEntity_ExtremeCoordinates_MapsCorrectly() {
        Location location = Location.of(90.0, 180.0, "North Pole", "Arctic");

        LocationEntity entity = mapper.toEntity(location);

        assertEquals(90.0, entity.latitude());
        assertEquals(180.0, entity.longitude());
        assertEquals("North Pole", entity.country());
        assertEquals("Arctic", entity.city());
    }

    @Test
    void testToDomain_ExtremeCoordinates_MapsCorrectly() {
        LocationEntity entity = LocationEntity.builder()
                .latitude(-90.0)
                .longitude(-180.0)
                .country("Antarctica")
                .city("South Pole")
                .build();

        Location location = mapper.toDomain(entity);

        assertEquals(-90.0, location.latitude());
        assertEquals(-180.0, location.longitude());
        assertEquals("Antarctica", location.country());
        assertEquals("South Pole", location.city());
    }

    @Test
    void testToEntity_PrecisionPreservation_MaintainsAccuracy() {
        Location location = Location.of(40.748817, -73.985428, "USA", "NYC");

        LocationEntity entity = mapper.toEntity(location);

        assertEquals(40.748817, entity.latitude(), 0.000001);
        assertEquals(-73.985428, entity.longitude(), 0.000001);
    }

    @Test
    void testToDomain_PrecisionPreservation_MaintainsAccuracy() {
        LocationEntity entity = LocationEntity.builder()
                .latitude(51.507351)
                .longitude(-0.127758)
                .country("UK")
                .city("London")
                .build();

        Location location = mapper.toDomain(entity);

        assertEquals(51.507351, location.latitude(), 0.000001);
        assertEquals(-0.127758, location.longitude(), 0.000001);
    }
}