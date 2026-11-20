package com.delivery.shipment.event;

import java.time.Instant;
import java.util.UUID;

public record ShipmentDetailsUpdatedEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        ShipmentPayload payload,
        boolean priceRecalculated
) {
    public static final String TYPE = "SHIPMENT_DETAILS_UPDATED";
}
