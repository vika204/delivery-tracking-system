package com.delivery.webui.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Per-client limit for the JSON API: at most {@code capacity} requests per {@code window}.
 * The bucket refills continuously, so a short burst up to {@code capacity} is allowed.
 */
@ConfigurationProperties(prefix = "rate-limit")
public record RateLimitProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("60") int capacity,
        @DefaultValue("1m") Duration window) {

    public RateLimitProperties {
        if (capacity < 1) {
            throw new IllegalArgumentException("rate-limit.capacity must be positive");
        }
        if (window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("rate-limit.window must be positive");
        }
    }
}
