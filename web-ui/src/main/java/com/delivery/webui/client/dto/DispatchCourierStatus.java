package com.delivery.webui.client.dto;

/** GET /couriers/{id}/status у dispatch-service. state: AVAILABLE / BUSY / UNAVAILABLE. */
public record DispatchCourierStatus(
        long courierId,
        boolean online,
        String state,
        long activeAssignments,
        Long pendingOfferId
) {
}
