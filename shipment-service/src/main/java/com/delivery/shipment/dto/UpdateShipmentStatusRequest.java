package com.delivery.shipment.dto;

import com.delivery.shipment.entity.ShipmentStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateShipmentStatusRequest(@NotNull ShipmentStatus status) {
}