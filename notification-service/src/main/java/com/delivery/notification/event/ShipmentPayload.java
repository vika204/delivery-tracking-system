package com.delivery.notification.event;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ShipmentPayload(
        @NotNull @Positive Long shipmentId,
        @Positive Long userId,
        String status
) {
}
