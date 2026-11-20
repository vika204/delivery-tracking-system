package com.delivery.shipment.exception;

public class ShipmentAccessDeniedException extends RuntimeException {

    public ShipmentAccessDeniedException(String message) {
        super(message);
    }
}
