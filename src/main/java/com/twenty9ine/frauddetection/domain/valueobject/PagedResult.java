package com.twenty9ine.frauddetection.domain.valueobject;

import java.util.List;

public record PagedResult<T>(
    List<T> content,
    int pageNumber,
    int pageSize,
    long totalElements,
    int totalPages
) {
    public static <T> PagedResult<T> of(List<T> content, int pageNumber, int pageSize, long totalElements) {
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);
        return new PagedResult<>(content, pageNumber, pageSize, totalElements, totalPages);
    }
}