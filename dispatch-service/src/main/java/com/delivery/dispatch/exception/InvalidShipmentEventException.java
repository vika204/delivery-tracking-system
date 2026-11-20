package com.delivery.dispatch.exception;

public class InvalidShipmentEventException extends RuntimeException {
    public InvalidShipmentEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
