package com.delivery.shipment.exception;

public class ShipmentNotFoundException extends RuntimeException {

    public ShipmentNotFoundException(Long shipmentId) {
        super("Shipment with id " + shipmentId + " was not found");
    }
}
