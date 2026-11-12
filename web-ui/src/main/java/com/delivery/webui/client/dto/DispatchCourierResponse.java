package com.delivery.webui.client.dto;

public record DispatchCourierResponse(
        Long shipmentId,
        boolean assigned,
        String courierName
) {
}
