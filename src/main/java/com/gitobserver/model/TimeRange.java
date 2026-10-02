package com.gitobserver.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

public record TimeRange(Instant start, Instant end) {
    public TimeRange {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Indica la fecha y la hora de inicio y de fin.");
        }
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("La fecha inicial no puede ser posterior a la final.");
        }
    }

    public static TimeRange of(
            LocalDate fromDate,
            int fromHour,
            int fromMinute,
            LocalDate toDate,
            int toHour,
            int toMinute,
            ZoneId zone
    ) {
        Instant start = LocalDateTime.of(fromDate, LocalTime.of(fromHour, fromMinute)).atZone(zone).toInstant();
        Instant end = LocalDateTime.of(toDate, LocalTime.of(toHour, toMinute)).atZone(zone).toInstant();
        return new TimeRange(start, end);
    }

    /** GitHub trata {@code until} como exclusivo. Este ajuste incluye el minuto elegido. */
    public Instant githubSince() {
        return start.minusSeconds(1);
    }

    public Instant githubUntil() {
        return end.plusSeconds(60);
    }
}
