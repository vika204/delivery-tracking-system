package com.delivery.shipment.dto;

import java.math.BigDecimal;

public record PriceBreakdown(
        BigDecimal basePrice,
        BigDecimal distanceKm,
        BigDecimal pricePerKm,
        BigDecimal distanceCharge,
        BigDecimal weightKg,
        BigDecimal pricePerKg,
        BigDecimal weightCharge,
        BigDecimal total
) {
}
