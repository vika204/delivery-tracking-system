package com.delivery.webui.grpc.dto;

import java.time.LocalDateTime;

public record TrackingLocationEvent(
        long locationId,
        long shipmentId,
        double latitude,
        double longitude,
        LocalDateTime recordedAt
) {
}
