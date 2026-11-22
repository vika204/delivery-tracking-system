package com.delivery.dispatch.event;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record ShipmentEventMessage(
        @NotNull UUID eventId,
        @NotBlank String eventType,
        Instant occurredAt,
        @NotNull @Valid ShipmentSnapshot payload
) {
    public static final String SHIPMENT_CREATED = "SHIPMENT_CREATED";
}
