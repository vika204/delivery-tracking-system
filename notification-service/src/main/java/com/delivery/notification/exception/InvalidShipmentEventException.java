package com.delivery.notification.exception;

//подія не відповідає контракту: повторна обробка нічого не змінить
public class InvalidShipmentEventException extends RuntimeException {

    public InvalidShipmentEventException(String message) {
        super(message);
    }
}
