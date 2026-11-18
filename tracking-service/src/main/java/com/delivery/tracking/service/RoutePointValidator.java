package com.delivery.tracking.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Перевірка однієї GPS-координати від кур'єра.
 * Не залежить від gRPC-повідомлень: спільна для Client Streaming і Bidirectional Streaming.
 */
@Component
public class RoutePointValidator {

    static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(5);

    private final Clock clock;

    @Autowired
    public RoutePointValidator() {
        this(Clock.systemUTC());
    }

    RoutePointValidator(Clock clock) {
        this.clock = clock;
    }

    /**
     * @param recordedAt null, якщо час не передано
     * @return опис порушення або empty, якщо координата коректна
     */
    public Optional<String> findViolation(double latitude, double longitude, Instant recordedAt) {
        // Порівняння «всередину діапазону» відсікає й NaN.
        if (!(latitude >= -90 && latitude <= 90)) {
            return Optional.of("latitude must be between -90 and 90");
        }
        if (!(longitude >= -180 && longitude <= 180)) {
            return Optional.of("longitude must be between -180 and 180");
        }
        if (recordedAt == null) {
            return Optional.of("recordedAt is required");
        }
        if (recordedAt.isAfter(Instant.now(clock).plus(MAX_CLOCK_SKEW))) {
            return Optional.of("recordedAt must not be in the future");
        }
        return Optional.empty();
    }
}
