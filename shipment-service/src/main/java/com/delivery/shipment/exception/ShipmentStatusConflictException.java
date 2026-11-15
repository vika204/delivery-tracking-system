package com.delivery.shipment.exception;

/** Дія недоступна в поточному стані доставки (409). */
public class ShipmentStatusConflictException extends RuntimeException {

    public ShipmentStatusConflictException(String message) {
        super(message);
    }
}
