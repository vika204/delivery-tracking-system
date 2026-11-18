package com.delivery.tracking.grpcclient;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Єдині параметри викликів tracking-service для всіх клієнтів: deadline і retry.
 * Ті самі значення використовує і retry-політика каналу, і ручне перепідключення стрімів.
 */
@ConfigurationProperties("tracking.grpc")
public record TrackingGrpcProperties(
        @DefaultValue("30s") Duration deadline,
        @DefaultValue Retry retry
) {

    public static final String CHANNEL = "tracking";

    public record Retry(
            @DefaultValue("5") int maxAttempts,
            @DefaultValue("500ms") Duration initialBackoff,
            @DefaultValue("4s") Duration maxBackoff,
            @DefaultValue("2.0") double backoffMultiplier
    ) {

        public Duration backoff(int attempt) {
            double millis = initialBackoff.toMillis() * Math.pow(backoffMultiplier, attempt - 1);
            return Duration.ofMillis((long) Math.min(millis, maxBackoff.toMillis()));
        }
    }
}
