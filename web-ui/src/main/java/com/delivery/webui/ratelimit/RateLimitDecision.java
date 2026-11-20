package com.delivery.webui.ratelimit;

/**
 * Outcome of one rate-limit check.
 *
 * @param retryAfterSeconds whole seconds until a request can succeed; 0 when {@code allowed}
 */
public record RateLimitDecision(boolean allowed, int limit, int remaining, long retryAfterSeconds) {
}
