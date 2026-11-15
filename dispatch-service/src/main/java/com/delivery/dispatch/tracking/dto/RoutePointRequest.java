package com.delivery.dispatch.tracking.dto;

import java.time.Instant;

public record RoutePointRequest(
        Double latitude,
        Double longitude,
        Instant recordedAt
) {
}
