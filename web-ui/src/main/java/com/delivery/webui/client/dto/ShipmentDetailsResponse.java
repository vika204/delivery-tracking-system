package com.delivery.webui.client.dto;

public record ShipmentDetailsResponse(
        Long shipmentId,
        String deliveryAddress
) {
}
