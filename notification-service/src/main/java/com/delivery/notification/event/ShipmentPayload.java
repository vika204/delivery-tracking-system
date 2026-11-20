package com.delivery.notification.event;

public record ShipmentPayload(
        Long shipmentId,
        Long userId,
        String status
) {
}
