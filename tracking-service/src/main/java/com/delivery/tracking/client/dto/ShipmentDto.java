package com.delivery.tracking.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ShipmentDto(
        Long shipmentId,
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
        String status,
        LocalDateTime createdAt
) {
}