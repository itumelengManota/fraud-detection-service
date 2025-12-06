package com.twenty9ine.frauddetection.domain.valueobject;

public record PageRequest(int pageNumber, int pageSize, String sortBy, SortDirection sortDirection) {

    public static PageRequest of(int pageNumber, int pageSize) {
        return new PageRequest(pageNumber, pageSize, "assessmentTime", SortDirection.DESC);
    }

    public static PageRequest of(int pageNumber, int pageSize, SortDirection sortDirection) {
        return new PageRequest(pageNumber, pageSize, "assessmentTime", sortDirection);
    }
}