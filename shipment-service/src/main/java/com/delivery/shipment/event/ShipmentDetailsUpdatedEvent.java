package com.delivery.shipment.event;

import java.time.Instant;
import java.util.UUID;

/** Відправник змінив деталі доставки до призначення кур'єра (US-1.6). */
public record ShipmentDetailsUpdatedEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        ShipmentPayload payload,
        boolean priceRecalculated
) {
    public static final String TYPE = "SHIPMENT_DETAILS_UPDATED";
}
