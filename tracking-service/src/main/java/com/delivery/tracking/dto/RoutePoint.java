package com.delivery.tracking.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RoutePoint(
        BigDecimal latitude,
        BigDecimal longitude,
        LocalDateTime recordedAt
) {
}
