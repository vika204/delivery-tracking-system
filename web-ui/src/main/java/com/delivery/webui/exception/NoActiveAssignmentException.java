package com.delivery.webui.exception;

public class NoActiveAssignmentException extends RuntimeException {

    public NoActiveAssignmentException(long courierId, long shipmentId) {
        super("Courier " + courierId + " has no active assignment for shipment " + shipmentId);
    }
}
