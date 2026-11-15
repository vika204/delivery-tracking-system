package com.delivery.notification.grpc.dto;

import java.time.Instant;

/**
 * Поточна інформація про доставку. latitude/longitude/recordedAt = null,
 * якщо для посилки ще немає жодної GPS-координати.
 */
public record DeliveryInfoResponse(
        long shipmentId,
        String status,
        Double latitude,
        Double longitude,
        Instant recordedAt
) {
}
