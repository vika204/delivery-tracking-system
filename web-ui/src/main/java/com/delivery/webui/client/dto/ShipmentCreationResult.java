package com.delivery.webui.client.dto;

public record ShipmentCreationResult(CreatedShipmentResponse shipment, boolean replayed) {
}
