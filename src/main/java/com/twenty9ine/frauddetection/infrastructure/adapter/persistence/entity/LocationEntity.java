package com.twenty9ine.frauddetection.infrastructure.adapter.persistence.entity;

import lombok.Builder;

import java.time.Instant;

@Builder
public record LocationEntity(double latitude, double longitude, String country, String city, Instant timestamp) {
}