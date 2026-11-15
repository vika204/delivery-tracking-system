package com.delivery.shipment.pricing;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Тариф: базова ставка + ставка за км + ставка за кг, далі множник типу доставки.
 * Габарити на ціну не впливають (рішення команди в dz_2).
 */
@ConfigurationProperties(prefix = "pricing")
public record TariffProperties(
        BigDecimal basePrice,
        BigDecimal pricePerKm,
        BigDecimal pricePerKg,
        BigDecimal expressMultiplier,
        Duration quoteTtl
) {
}
