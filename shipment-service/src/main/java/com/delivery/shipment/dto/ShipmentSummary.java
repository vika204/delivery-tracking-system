package com.delivery.shipment.dto;

import com.delivery.shipment.entity.Shipment;
import com.delivery.shipment.entity.ShipmentStatus;

public record ShipmentSummary(
        Long shipmentId,
        ShipmentStatus status,
        String pickupAddress,
        String deliveryAddress
) {

    public static ShipmentSummary from(Shipment shipment) {
        return new ShipmentSummary(
                shipment.getShipmentId(),
                shipment.getStatus(),
                shipment.getPickupAddress(),
                shipment.getDeliveryAddress()
        );
    }
}
