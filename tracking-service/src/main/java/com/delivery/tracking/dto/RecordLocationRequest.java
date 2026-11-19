package com.delivery.tracking.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** GPS-точка кур'єра, надіслана через REST. */
public record RecordLocationRequest(
        BigDecimal latitude,
        BigDecimal longitude,
        Instant recordedAt
) {
}
