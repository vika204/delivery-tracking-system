package com.delivery.webui.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

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
