package com.delivery.tracking.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * GPS-точка кур'єра, надіслана через REST.
 *
 * @param pointId ідентифікатор точки від клієнта; повтор з тим самим pointId повертає вже збережену точку
 */
public record RecordLocationRequest(
        UUID pointId,
        BigDecimal latitude,
        BigDecimal longitude,
        Instant recordedAt
) {
}
