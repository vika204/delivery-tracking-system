package com.delivery.shipment.dto;

import com.delivery.shipment.entity.Shipment;
import com.delivery.shipment.entity.ShipmentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public record ShipmentResponse(
        Long shipmentId,
        Long userId,
        String senderName,
        String senderPhone,
        String recipientName,
        String recipientPhone,
        String pickupAddress,
        String pickupCityCode,
        Double pickupLatitude,
        Double pickupLongitude,
        String deliveryAddress,
        String deliveryCityCode,
        Double deliveryLatitude,
        Double deliveryLongitude,
        BigDecimal weight,
        BigDecimal length,
        BigDecimal width,
        BigDecimal height,
        BigDecimal distance,
        BigDecimal price,
        UUID quoteId,
        ShipmentStatus status,
        Long assignedCourierId,
        String assignedCourierName,
        Instant assignedAt,
        Instant tripStartedAt,
        Instant pickedUpAt,
        Instant deliveredAt,
        Instant cancelledAt,
        String cancellationReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ShipmentResponse from(Shipment shipment) {
        return new ShipmentResponse(
                shipment.getShipmentId(),
                shipment.getUserId(),
                shipment.getSenderName(),
                shipment.getSenderPhone(),
                shipment.getRecipientName(),
                shipment.getRecipientPhone(),
                shipment.getPickupAddress(),
                shipment.getPickupCityCode(),
                shipment.getPickupLatitude(),
                shipment.getPickupLongitude(),
                shipment.getDeliveryAddress(),
                shipment.getDeliveryCityCode(),
                shipment.getDeliveryLatitude(),
                shipment.getDeliveryLongitude(),
                shipment.getWeight(),
                shipment.getLength(),
                shipment.getWidth(),
                shipment.getHeight(),
                shipment.getDistance(),
                shipment.getPrice(),
                shipment.getQuoteId(),
                shipment.getStatus(),
                shipment.getAssignedCourierId(),
                shipment.getAssignedCourierName(),
                shipment.getAssignedAt(),
                shipment.getTripStartedAt(),
                shipment.getPickedUpAt(),
                shipment.getDeliveredAt(),
                shipment.getCancelledAt(),
                shipment.getCancellationReason(),
                shipment.getCreatedAt(),
                shipment.getUpdatedAt()
        );
    }
}
