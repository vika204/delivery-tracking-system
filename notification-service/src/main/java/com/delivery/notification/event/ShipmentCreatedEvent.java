package com.delivery.notification.event;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;

public record ShipmentCreatedEvent(
        @NotNull UUID eventId,
        String eventType,
        Instant occurredAt,
        @NotNull @Valid ShipmentPayload payload
) {
    public static final String TYPE = "SHIPMENT_CREATED";
}
