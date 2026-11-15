package com.delivery.dispatch.tracking.dto;

import java.time.Instant;

public record LiveLocationPointRequest(
        String pointId,
        Double latitude,
        Double longitude,
        Instant recordedAt
) {
}
