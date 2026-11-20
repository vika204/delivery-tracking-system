package com.delivery.webui.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBucketRateLimiterTest {

    private final MutableClock clock = new MutableClock();
    private final TokenBucketRateLimiter limiter =
            new TokenBucketRateLimiter(new RateLimitProperties(true, 3, Duration.ofSeconds(30)), clock);

    @Test
    void allowsBurstUpToCapacityAndCountsRemaining() {
        assertThat(limiter.tryAcquire("a")).isEqualTo(new RateLimitDecision(true, 3, 2, 0));
        assertThat(limiter.tryAcquire("a")).isEqualTo(new RateLimitDecision(true, 3, 1, 0));
        assertThat(limiter.tryAcquire("a")).isEqualTo(new RateLimitDecision(true, 3, 0, 0));
    }

    @Test
    void rejectsOverLimitWithRetryAfterUntilNextToken() {
        exhaust("a");

        RateLimitDecision decision = limiter.tryAcquire("a");

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.remaining()).isZero();
        assertThat(decision.retryAfterSeconds()).isEqualTo(10); // 3 tokens per 30 s = 1 token per 10 s
    }

    @Test
    void retryAfterShrinksAsTimePasses() {
        exhaust("a");
        clock.advance(Duration.ofSeconds(4));

        assertThat(limiter.tryAcquire("a").retryAfterSeconds()).isEqualTo(6);
    }

    @Test
    void refillsOneTokenAfterRefillInterval() {
        exhaust("a");
        clock.advance(Duration.ofSeconds(10));

        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("a").allowed()).isFalse();
    }

    @Test
    void neverRefillsAboveCapacity() {
        limiter.tryAcquire("a");
        clock.advance(Duration.ofHours(1));

        assertThat(limiter.tryAcquire("a").remaining()).isEqualTo(2);
    }

    @Test
    void retryAfterIsAtLeastOneSecond() {
        TokenBucketRateLimiter fast =
                new TokenBucketRateLimiter(new RateLimitProperties(true, 1000, Duration.ofSeconds(1)), clock);
        for (int i = 0; i < 1000; i++) {
            fast.tryAcquire("a");
        }

        assertThat(fast.tryAcquire("a").retryAfterSeconds()).isEqualTo(1);
    }

    @Test
    void clientsDoNotShareBuckets() {
        exhaust("a");

        assertThat(limiter.tryAcquire("a").allowed()).isFalse();
        assertThat(limiter.tryAcquire("b").allowed()).isTrue();
    }

    @Test
    void dropsFullyRefilledClientsOnceTooManyAreTracked() {
        for (int i = 0; i <= TokenBucketRateLimiter.CLEANUP_THRESHOLD; i++) {
            limiter.tryAcquire("client-" + i);
        }
        clock.advance(Duration.ofSeconds(30));

        limiter.tryAcquire("new-client");

        assertThat(limiter.trackedClients()).isEqualTo(1);
    }

    private void exhaust(String key) {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire(key);
        }
    }
}
