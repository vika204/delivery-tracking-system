package com.delivery.shipment.dto;

import com.delivery.shipment.entity.DeliveryType;

import java.math.BigDecimal;

/**
 * Розбивка вартості (US-2.5): базова ставка -> надбавка за відстань -> надбавка за вагу
 * -> множник типу доставки -> підсумок.
 */
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
