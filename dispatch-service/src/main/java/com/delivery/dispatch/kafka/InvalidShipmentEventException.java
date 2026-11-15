package com.delivery.dispatch.kafka;

/** Подію неможливо розібрати або вона порушує контракт: повтор не допоможе. */
public class InvalidShipmentEventException extends RuntimeException {
    public InvalidShipmentEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
