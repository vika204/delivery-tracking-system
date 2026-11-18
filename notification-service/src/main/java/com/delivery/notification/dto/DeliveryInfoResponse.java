package com.delivery.notification.dto;

import java.time.Instant;

public record DeliveryInfoResponse(
        long shipmentId,
        String status,
        Double latitude,
        Double longitude,
        Instant recordedAt
) {
}
