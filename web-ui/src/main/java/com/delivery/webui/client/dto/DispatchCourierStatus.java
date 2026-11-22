package com.delivery.webui.client.dto;

public record DispatchCourierStatus(
        long courierId,
        String displayName,
        boolean online,
        String state,
        long activeAssignments,
        Long pendingOfferId
) {
}
