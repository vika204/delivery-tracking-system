package com.delivery.shipment.exception;

/** Подію неможливо розібрати або вона порушує контракт: повтор не допоможе. */
public class InvalidDispatchEventException extends RuntimeException {

    public InvalidDispatchEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
