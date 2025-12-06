package com.twenty9ine.frauddetection.application.dto;

import java.util.List;

public record PagedResultDto<T>(
    List<T> content,
    int pageNumber,
    int pageSize,
    long totalElements,
    int totalPages
) {
}