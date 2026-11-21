package com.delivery.webui.client.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RecordLocationRequest(
        UUID pointId,
        @NotNull(message = "latitude is required")
        @DecimalMin(value = "-90", message = "latitude must be between -90 and 90")
        @DecimalMax(value = "90", message = "latitude must be between -90 and 90") BigDecimal latitude,
        @NotNull(message = "longitude is required")
        @DecimalMin(value = "-180", message = "longitude must be between -180 and 180")
        @DecimalMax(value = "180", message = "longitude must be between -180 and 180") BigDecimal longitude,
        @NotNull(message = "recordedAt is required") Instant recordedAt
) {
}
