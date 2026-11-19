package com.delivery.webui.client.dto;

public record DispatchCourierAssignment(
        long assignmentId,
        long shipmentId,
        String status,
        boolean pickedUp,
        String pickupAddress,
        String deliveryAddress
) {
}
