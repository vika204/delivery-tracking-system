package com.delivery.dispatch.event;

import java.time.Instant;
import java.util.UUID;

/** SHIPMENT_STATUS_CHANGED (shipment.status.changed). */
public record ShipmentStatusChangedMessage(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        Long shipmentId,
        String status,
        String previousStatus,
        Long courierId,
        String actorType,
        String reason
) {
    public static final String TYPE = "SHIPMENT_STATUS_CHANGED";
}
