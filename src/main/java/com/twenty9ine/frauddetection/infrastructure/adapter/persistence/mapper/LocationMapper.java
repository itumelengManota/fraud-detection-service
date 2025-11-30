package com.twenty9ine.frauddetection.infrastructure.adapter.persistence.mapper;

import com.twenty9ine.frauddetection.domain.valueobject.Location;
import com.twenty9ine.frauddetection.infrastructure.adapter.persistence.entity.LocationEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface LocationMapper {

    @Mapping(target = "id", ignore = true)
    LocationEntity toEntity(Location location);

    default Location toDomain(LocationEntity entity) {
        if (entity == null) return null;
        return Location.of(
                entity.getLatitude(),
                entity.getLongitude(),
                entity.getCountry(),
                entity.getCity(),
                entity.getTimestamp()
        );
    }
}