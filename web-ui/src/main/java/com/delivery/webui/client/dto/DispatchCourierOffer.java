package com.delivery.webui.client.dto;

import java.math.BigDecimal;

public record DispatchCourierOffer(
        long offerId,
        long shipmentId,
        long secondsLeft,
        String pickupAddress,
        String deliveryAddress,
        BigDecimal parcelWeight,
        BigDecimal distanceKm,
        Double distanceToPickupKm,
        BigDecimal price,
        String deliveryType
) {
}
