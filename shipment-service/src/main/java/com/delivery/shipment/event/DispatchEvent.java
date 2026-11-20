package com.delivery.shipment.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Подія Dispatch (топік dispatch.events).
 * COURIER_ASSIGNED / COURIER_UNASSIGNED змінюють доставку; COURIER_SEARCH_DELAYED — лише
 * сигнал для сповіщень (courierId = null), Shipment його не обробляє.
 */
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

    public boolean changesAssignment() {
        return COURIER_ASSIGNED.equals(eventType) || COURIER_UNASSIGNED.equals(eventType);
    }
}
