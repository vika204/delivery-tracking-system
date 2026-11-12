package com.delivery.webui.controller.dto;

public record TrackingShipmentStatusResponse(
        long shipmentId,
        String status
) {
}
