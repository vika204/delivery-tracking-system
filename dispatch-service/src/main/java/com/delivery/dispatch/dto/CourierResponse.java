package com.delivery.dispatch.dto;

import com.delivery.dispatch.entity.Courier;

import java.math.BigDecimal;
import java.time.Instant;

public record CourierResponse(
        Long userId,
        String displayName,
        String phone,
        String workZone,
        String workZoneCode,
        BigDecimal maxPackageWeight,
        boolean available,
        Instant lastAssignedAt
) {

    public static CourierResponse from(Courier courier) {
        return new CourierResponse(
                courier.getUserId(),
                courier.getDisplayName(),
                courier.getPhone(),
                courier.getWorkZone(),
                courier.getWorkZoneCode(),
                courier.getMaxPackageWeight(),
                courier.isAvailable(),
                courier.getLastAssignedAt()
        );
    }
}
