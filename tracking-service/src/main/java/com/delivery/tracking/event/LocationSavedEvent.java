package com.delivery.tracking.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LocationSavedEvent(
        Long locationId,
        Long shipmentId,
        BigDecimal latitude,
        BigDecimal longitude,
        LocalDateTime recordedAt
) {
}