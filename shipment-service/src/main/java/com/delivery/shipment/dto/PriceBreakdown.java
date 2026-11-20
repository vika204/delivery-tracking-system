package com.delivery.shipment.dto;

import com.delivery.shipment.entity.DeliveryType;

import java.math.BigDecimal;

public record PriceBreakdown(
        BigDecimal basePrice,
        BigDecimal distanceKm,
        BigDecimal pricePerKm,
        BigDecimal distanceCharge,
        BigDecimal weightKg,
        BigDecimal pricePerKg,
        BigDecimal weightCharge,
        BigDecimal subtotal,
        DeliveryType deliveryType,
        BigDecimal deliveryTypeMultiplier,
        BigDecimal total
) {
}
