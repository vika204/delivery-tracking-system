package com.delivery.dispatch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param offerTtl              скільки кур'єр має часу відповісти на пропозицію (US-3.2)
 * @param locationMaxAge        геопозиція старша за це значення не використовується в ранжуванні
 * @param queueBatchSize        скільки доставок з черги очікування обробляти за один прохід
 * @param searchDelayThreshold  після скількох хвилин без кур'єра публікується COURIER_SEARCH_DELAYED (US-3.1)
 */
@ConfigurationProperties(prefix = "dispatch")
public record DispatchProperties(
        Duration offerTtl,
        Duration locationMaxAge,
        int queueBatchSize,
        Duration searchDelayThreshold
) {
}
