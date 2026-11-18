package com.delivery.dispatch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("tracking.grpc")
public record TrackingGrpcProperties(Duration deadline, Retry retry) {

    public record Retry(int maxAttempts, Duration initialBackoff, Duration maxBackoff, double backoffMultiplier) {

        public Duration backoff(int attempt) {
            double millis = initialBackoff.toMillis() * Math.pow(backoffMultiplier, attempt - 1);
            return Duration.ofMillis((long) Math.min(millis, maxBackoff.toMillis()));
        }
    }
}
