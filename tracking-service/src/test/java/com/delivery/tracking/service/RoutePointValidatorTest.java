package com.delivery.tracking.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class RoutePointValidatorTest {

    private static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");

    private final RoutePointValidator validator = new RoutePointValidator(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void acceptsValidPoint() {
        assertThat(validator.findViolation(50.45, 30.52, NOW)).isEmpty();
    }

    @Test
    void rejectsOutOfRangeAndNaNCoordinates() {
        assertThat(validator.findViolation(95, 30.52, NOW)).contains("latitude must be between -90 and 90");
        assertThat(validator.findViolation(Double.NaN, 30.52, NOW)).contains("latitude must be between -90 and 90");
        assertThat(validator.findViolation(50.45, 181, NOW)).contains("longitude must be between -180 and 180");
    }

    @Test
    void rejectsMissingTime() {
        assertThat(validator.findViolation(50.45, 30.52, null)).contains("recordedAt is required");
    }

    @Test
    void toleratesClockSkewButRejectsFutureTime() {
        assertThat(validator.findViolation(50.45, 30.52, NOW.plus(RoutePointValidator.MAX_CLOCK_SKEW))).isEmpty();
        assertThat(validator.findViolation(50.45, 30.52, NOW.plus(RoutePointValidator.MAX_CLOCK_SKEW).plusSeconds(1)))
                .contains("recordedAt must not be in the future");
    }
}
