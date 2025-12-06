package com.twenty9ine.frauddetection.application.port.in.query;

public record PageRequestQuery(int pageNumber, int pageSize, String sortBy, String sortDirection) {
    public static PageRequestQuery of(int pageNumber, int pageSize) {
        return new PageRequestQuery(pageNumber, pageSize, "assessmentTime", "DESC");
    }
}