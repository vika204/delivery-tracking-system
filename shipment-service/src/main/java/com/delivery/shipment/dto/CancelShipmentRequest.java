package com.delivery.shipment.dto;

import jakarta.validation.constraints.Size;

public record CancelShipmentRequest(@Size(max = 500) String reason) {
}
