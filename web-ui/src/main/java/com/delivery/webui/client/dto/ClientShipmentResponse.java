package com.delivery.webui.client.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ClientShipmentResponse(
        long shipmentId,
        long userId,
        String status,
        String pickupAddress,
        String deliveryAddress,
        String recipientName,
        String recipientPhone,
        LocalDateTime createdAt,
        BigDecimal distance,
        BigDecimal weight,
        BigDecimal length,
        BigDecimal width,
        BigDecimal height,
        BigDecimal price,
        Long assignedCourierId,
        String assignedCourierName
) {
}
