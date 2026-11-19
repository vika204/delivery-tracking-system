package com.delivery.webui.client.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * GPS-точка кур'єра; координати й час перевіряє tracking-service.
 *
 * @param pointId ідентифікатор точки від браузера: повтор після збою не створює дубля
 */
public record RecordLocationRequest(
        UUID pointId,
        BigDecimal latitude,
        BigDecimal longitude,
        Instant recordedAt
) {
}
