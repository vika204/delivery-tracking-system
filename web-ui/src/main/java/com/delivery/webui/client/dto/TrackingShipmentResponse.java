package com.delivery.webui.client.dto;

public record TrackingShipmentResponse(
        Long shipmentId,
        String recipientName,
        String status
) {
}