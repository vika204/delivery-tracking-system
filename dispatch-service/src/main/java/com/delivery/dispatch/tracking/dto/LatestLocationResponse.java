package com.delivery.dispatch.tracking.dto;

import java.time.Instant;

public record LatestLocationResponse(
        long locationId,
        long shipmentId,
        double latitude,
        double longitude,
        Instant recordedAt
) {
}
