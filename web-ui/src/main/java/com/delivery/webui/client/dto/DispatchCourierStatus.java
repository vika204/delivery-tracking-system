package com.delivery.webui.client.dto;

public record DispatchCourierStatus(
        long courierId,
        boolean online,
        String state,
        long activeAssignments,
        Long pendingOfferId
) {
}
