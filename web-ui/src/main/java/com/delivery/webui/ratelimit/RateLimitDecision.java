package com.delivery.webui.ratelimit;

public record RateLimitDecision(boolean allowed, int limit, int remaining, long retryAfterSeconds) {
}
