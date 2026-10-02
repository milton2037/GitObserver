package com.gitobserver.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TimeRangeTest {
    @Test
    void includesTheSelectedMinuteForGitHub() {
        ZoneId zone = ZoneId.of("America/Bogota");
        TimeRange range = TimeRange.of(
                LocalDate.of(2026, 10, 1), 18, 30,
                LocalDate.of(2026, 10, 1), 18, 30,
                zone
        );

        assertEquals(range.start().minusSeconds(1), range.githubSince());
        assertEquals(range.end().plusSeconds(60), range.githubUntil());
    }

    @Test
    void rejectsAnEndBeforeTheStart() {
        ZoneId zone = ZoneId.of("America/Bogota");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> TimeRange.of(
                LocalDate.of(2026, 10, 2), 9, 0,
                LocalDate.of(2026, 10, 1), 18, 0,
                zone
        ));
        assertFalse(error.getMessage().isBlank());
    }
}
