package com.delivery.shipment.exception;

public class ShipmentStatusConflictException extends RuntimeException {

    public ShipmentStatusConflictException(Long shipmentId) {
        super("Cannot change terminal status for shipment " + shipmentId);
    }
}