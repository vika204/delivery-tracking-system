package com.delivery.shipment.dto;

import com.delivery.shipment.entity.Shipment;
import com.delivery.shipment.entity.ShipmentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.stream.Collectors;

public record CourierShipmentView(
        Long shipmentId,
        ShipmentStatus status,
        String pickupAddress,
        String deliveryAddress,
        Contact sender,
        Contact recipient,
        BigDecimal weight,
        BigDecimal length,
        BigDecimal width,
        BigDecimal height,
        BigDecimal distance,
        BigDecimal price,
        Instant assignedAt,
        Instant tripStartedAt,
        Instant pickedUpAt,
        Instant deliveredAt
) {

    public record Contact(String initials, String phone) {
    }

    public static CourierShipmentView from(Shipment shipment) {
        return new CourierShipmentView(
                shipment.getShipmentId(),
                shipment.getStatus(),
                shipment.getPickupAddress(),
                shipment.getDeliveryAddress(),
                new Contact(initials(shipment.getSenderName()), shipment.getSenderPhone()),
                new Contact(initials(shipment.getRecipientName()), shipment.getRecipientPhone()),
                shipment.getWeight(),
                shipment.getLength(),
                shipment.getWidth(),
                shipment.getHeight(),
                shipment.getDistance(),
                shipment.getPrice(),
                shipment.getAssignedAt(),
                shipment.getTripStartedAt(),
                shipment.getPickedUpAt(),
                shipment.getDeliveredAt()
        );
    }

    static String initials(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return null;
        }
        return Arrays.stream(fullName.trim().split("\\s+"))
                .map(part -> part.substring(0, 1).toUpperCase() + ".")
                .collect(Collectors.joining(" "));
    }
}
