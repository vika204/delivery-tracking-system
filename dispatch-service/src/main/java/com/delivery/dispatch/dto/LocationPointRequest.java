package com.delivery.dispatch.dto;

import java.time.Instant;

public record LocationPointRequest(
        String pointId,
        Double latitude,
        Double longitude,
        Instant recordedAt
) {
}
