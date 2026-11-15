package com.delivery.dispatch.dto;

import com.delivery.dispatch.entity.OfferStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

/** Пропозиція доставки з деталями маршруту й винагороди (US-3.2). */
public record OfferResponse(
        long offerId,
        long shipmentId,
        OfferStatus status,
        LocalDateTime expiresAt,
        long secondsLeft,
        String pickupAddress,
        String deliveryAddress,
        BigDecimal parcelWeight,
        BigDecimal distanceKm,
        Double distanceToPickupKm,
        BigDecimal price,
        String deliveryType,
        Instant createdAt
) {
}
