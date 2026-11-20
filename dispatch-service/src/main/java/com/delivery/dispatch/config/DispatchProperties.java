package com.delivery.dispatch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "dispatch")
public record DispatchProperties(
        Duration offerTtl,
        Duration locationMaxAge,
        int queueBatchSize,
        Duration searchDelayThreshold
) {
}
