package com.twenty9ine.frauddetection.infrastructure.adapter.persistence.mapper;

import com.twenty9ine.frauddetection.domain.valueobject.Location;
import com.twenty9ine.frauddetection.infrastructure.adapter.persistence.entity.LocationEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface LocationMapper {

    LocationEntity toEntity(Location location);

    Location toDomain(LocationEntity entity);
}