package com.delivery.shipment.dto;

import com.delivery.shipment.entity.Shipment;
import com.delivery.shipment.entity.ShipmentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ShipmentResponse(
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
        ShipmentStatus status,
        LocalDateTime createdAt
) {

    public static ShipmentResponse from(Shipment shipment) {
        return new ShipmentResponse(
                shipment.getShipmentId(),
                shipment.getUserId(),
                shipment.getRecipientName(),
                shipment.getRecipientPhone(),
                shipment.getPickupAddress(),
                shipment.getDeliveryAddress(),
                shipment.getWeight(),
                shipment.getLength(),
                shipment.getWidth(),
                shipment.getHeight(),
                shipment.getDistance(),
                shipment.getPrice(),
                shipment.getStatus(),
                shipment.getCreatedAt()
        );
    }
}
