package com.delivery.events;

import java.time.Instant;
import java.util.UUID;

public record ShipmentStatusChangedEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        Long shipmentId,
        String status
) {
    public static final String TYPE = "SHIPMENT_STATUS_CHANGED";
}