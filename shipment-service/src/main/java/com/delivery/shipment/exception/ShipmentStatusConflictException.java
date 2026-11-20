package com.delivery.shipment.exception;

public class ShipmentStatusConflictException extends RuntimeException {

    public ShipmentStatusConflictException(String message) {
        super(message);
    }
}
