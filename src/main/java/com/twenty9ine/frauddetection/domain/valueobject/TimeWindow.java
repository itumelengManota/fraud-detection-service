package com.twenty9ine.frauddetection.domain.valueobject;

import lombok.Getter;

import java.time.Duration;

@Getter
public enum TimeWindow {
    FIVE_MINUTES(Duration.ofMinutes(5), "5min"),
    ONE_HOUR(Duration.ofHours(1), "1hour"),
    TWENTY_FOUR_HOURS(Duration.ofHours(24), "24hour");

    private final Duration duration;
    private final String label;

    TimeWindow(Duration duration, String label) {
        this.duration = duration;
        this.label = label;
    }
}
