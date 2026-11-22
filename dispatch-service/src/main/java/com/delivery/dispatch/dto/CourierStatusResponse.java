package com.delivery.dispatch.dto;

import com.delivery.dispatch.entity.CourierAvailability;

public record CourierStatusResponse(
        long courierId,
        String displayName,
        boolean online,
        CourierAvailability state,
        long activeAssignments,
        Long pendingOfferId
) {
}
