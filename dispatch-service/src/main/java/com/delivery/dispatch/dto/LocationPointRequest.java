package com.delivery.dispatch.dto;

import java.time.Instant;

public record LocationPointRequest(
        Double latitude,
        Double longitude,
        Instant recordedAt
) {
}
