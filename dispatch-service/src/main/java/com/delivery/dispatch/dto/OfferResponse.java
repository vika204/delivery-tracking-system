package com.delivery.dispatch.dto;

import com.delivery.dispatch.entity.OfferStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

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
