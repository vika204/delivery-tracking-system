package com.delivery.webui.client.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** GPS-точка кур'єра; координати й час перевіряє tracking-service. */
public record RecordLocationRequest(
        BigDecimal latitude,
        BigDecimal longitude,
        Instant recordedAt
) {
}
