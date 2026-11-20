package com.delivery.shipment.event;

import java.time.Instant;
import java.util.UUID;

public record ShipmentStatusChangedEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        Long shipmentId,
        String status,
        String previousStatus,
        Long courierId,
        String actorType,
        String reason,
        Long senderId,
        String courierName,
        Double pickupLatitude,
        Double pickupLongitude,
        Double deliveryLatitude,
        Double deliveryLongitude
) {
    public static final String TYPE = "SHIPMENT_STATUS_CHANGED";
}
