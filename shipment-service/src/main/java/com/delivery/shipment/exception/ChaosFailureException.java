package com.delivery.shipment.exception;

public class ChaosFailureException extends RuntimeException {

    public ChaosFailureException() {
        super("Injected failure (chaos)");
    }
}
