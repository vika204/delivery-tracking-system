package com.delivery.dispatch.dto;

import com.delivery.dispatch.entity.CourierAvailability;

public record CourierStatusResponse(
        long courierId,
        boolean online,
        CourierAvailability state,
        long activeAssignments,
        Long pendingOfferId
) {
}
