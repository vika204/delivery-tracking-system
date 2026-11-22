package com.delivery.dispatch.event;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.UUID;

public record ShipmentStatusChangedMessage(
        @NotNull UUID eventId,
        @NotNull @Pattern(regexp = "SHIPMENT_STATUS_CHANGED", message = "must be SHIPMENT_STATUS_CHANGED") String eventType,
        Instant occurredAt,
        @NotNull @Positive Long shipmentId,
        @NotBlank String status,
        String previousStatus,
        Long courierId,
        String actorType,
        String reason
) {
    public static final String TYPE = "SHIPMENT_STATUS_CHANGED";
}
