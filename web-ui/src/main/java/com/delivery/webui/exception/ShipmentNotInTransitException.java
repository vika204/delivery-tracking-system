package com.delivery.webui.exception;

public class ShipmentNotInTransitException extends RuntimeException {

    public ShipmentNotInTransitException(long shipmentId, String status) {
        super("GPS can be recorded only while shipment " + shipmentId + " is IN_TRANSIT, current status is " + status);
    }
}
