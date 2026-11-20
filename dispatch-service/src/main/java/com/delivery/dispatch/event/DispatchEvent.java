package com.delivery.dispatch.event;

import java.time.Instant;
import java.util.UUID;

public record DispatchEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        Long shipmentId,
        Long courierId,
        String courierName,
        Long assignmentId,
        String reason,
        Long senderId,
        Instant waitingSince
) {
    public static final String COURIER_ASSIGNED = "COURIER_ASSIGNED";
    public static final String COURIER_UNASSIGNED = "COURIER_UNASSIGNED";
    public static final String COURIER_SEARCH_DELAYED = "COURIER_SEARCH_DELAYED";
}
