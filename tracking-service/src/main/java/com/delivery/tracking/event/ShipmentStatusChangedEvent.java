package com.delivery.tracking.event;

import java.time.Instant;
import java.util.UUID;

public record ShipmentStatusChangedEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        Long shipmentId,
        String status
) {
}