package com.delivery.tracking.client.dto;

import java.math.BigDecimal;

public record CreateShipmentRequest(
        Long userId,
        String recipientName,
        String recipientPhone,
        String pickupAddress,
        String deliveryAddress,
        BigDecimal weight,
        BigDecimal length,
        BigDecimal width,
        BigDecimal height,
        BigDecimal distance,
        BigDecimal price,
        String status
) {
}