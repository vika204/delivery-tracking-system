package com.delivery.dispatch.events;

import java.time.Instant;
import java.util.UUID;

/** Подія Dispatch (топік dispatch.events). */
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
    // AcceptCourierProposal -> CourierAssigned
    public static final String COURIER_ASSIGNED = "COURIER_ASSIGNED";
    // DeclineAssignment -> CourierUnassigned
    public static final String COURIER_UNASSIGNED = "COURIER_UNASSIGNED";
    // Кур'єра не знайдено довше за поріг (US-3.1): сигнал для сповіщення відправника; courierId = null.
    public static final String COURIER_SEARCH_DELAYED = "COURIER_SEARCH_DELAYED";
}
