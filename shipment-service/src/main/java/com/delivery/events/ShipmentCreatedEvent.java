package com.delivery.events;

import java.time.Instant;
import java.util.UUID;

public record ShipmentCreatedEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        ShipmentPayload payload
) {
    public static final String TYPE = "SHIPMENT_CREATED";
}
