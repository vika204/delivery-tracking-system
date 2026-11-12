package com.delivery.webui.client.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TrackingLocationResponse(
        Long locationId,
        Long shipmentId,
        BigDecimal latitude,
        BigDecimal longitude,
        LocalDateTime recordedAt
) {
}
