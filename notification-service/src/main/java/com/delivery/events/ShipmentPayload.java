package com.delivery.events;

public record ShipmentPayload(
        Long shipmentId,
        Long userId,
        String status
) {
}
