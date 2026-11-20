package com.delivery.shipment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;

@ConfigurationProperties(prefix = "pricing")
public record TariffProperties(
        BigDecimal basePrice,
        BigDecimal pricePerKm,
        BigDecimal pricePerKg,
        BigDecimal expressMultiplier,
        Duration quoteTtl
) {
}
