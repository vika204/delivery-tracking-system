package com.delivery.dispatch.dto;

public record ShipmentCourierResponse(
        Long shipmentId,
        boolean assigned,
        String courierName
) {
}
