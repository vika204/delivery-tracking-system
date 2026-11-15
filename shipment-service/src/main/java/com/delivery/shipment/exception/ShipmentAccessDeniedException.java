package com.delivery.shipment.exception;

/** Користувач не є відправником або призначеним кур'єром цієї доставки (403). */
public class ShipmentAccessDeniedException extends RuntimeException {

    public ShipmentAccessDeniedException(String message) {
        super(message);
    }
}
