package com.delivery.webui.ratelimit;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;

public class TokenBucketRateLimiter {

    static final int CLEANUP_THRESHOLD = 10_000;

    private final int capacity;
    private final double refillPerMillis;
    private final Clock clock;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public TokenBucketRateLimiter(RateLimitProperties properties, Clock clock) {
        this.capacity = properties.capacity();
        this.refillPerMillis = (double) properties.capacity() / properties.window().toMillis();
        this.clock = clock;
    }

    public RateLimitDecision tryAcquire(String key) {
        long now = clock.millis();
        if (buckets.size() > CLEANUP_THRESHOLD) {
            evictIdle(now);
        }
        return buckets.computeIfAbsent(key, k -> new Bucket(capacity, now)).tryConsume(now);
    }

    int trackedClients() {
        return buckets.size();
    }

    private void evictIdle(long now) {
        buckets.values().removeIf(bucket -> bucket.isFullAt(now));
    }

    private final class Bucket {
        private double tokens;
        private long lastRefill;

        Bucket(double tokens, long now) {
            this.tokens = tokens;
            this.lastRefill = now;
        }

        synchronized RateLimitDecision tryConsume(long now) {
            refill(now);
            if (tokens >= 1) {
                tokens -= 1;
                return new RateLimitDecision(true, capacity, (int) tokens, 0);
            }
            long retryAfter = (long) Math.ceil((1 - tokens) / refillPerMillis / 1000);
            return new RateLimitDecision(false, capacity, 0, Math.max(1, retryAfter));
        }

        synchronized boolean isFullAt(long now) {
            refill(now);
            return tokens >= capacity;
        }

        private void refill(long now) {
            tokens = Math.min(capacity, tokens + (now - lastRefill) * refillPerMillis);
            lastRefill = now;
        }
    }
}
