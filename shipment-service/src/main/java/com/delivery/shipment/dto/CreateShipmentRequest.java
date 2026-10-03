package com.delivery.shipment.dto;

import com.delivery.shipment.entity.ShipmentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateShipmentRequest(
        @NotNull @Positive Long userId,
        @NotBlank String recipientName,
        @NotBlank String recipientPhone,
        @NotBlank String pickupAddress,
        @NotBlank String deliveryAddress,
        @NotNull @Positive BigDecimal weight,
        @NotNull @Positive BigDecimal length,
        @NotNull @Positive BigDecimal width,
        @NotNull @Positive BigDecimal height,
        @NotNull @Positive BigDecimal distance,
        @NotNull @Positive BigDecimal price,
        @NotNull ShipmentStatus status
) {
}
