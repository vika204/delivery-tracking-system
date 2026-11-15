package com.delivery.dispatch.events;

import java.time.Instant;
import java.util.UUID;

/** SHIPMENT_CREATED (shipment.events) або SHIPMENT_DETAILS_UPDATED (shipment.details.updated). */
public record ShipmentEventMessage(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        ShipmentSnapshot payload
) {
    public static final String SHIPMENT_CREATED = "SHIPMENT_CREATED";
    public static final String SHIPMENT_DETAILS_UPDATED = "SHIPMENT_DETAILS_UPDATED";
}
