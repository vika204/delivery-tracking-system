package com.delivery.shipment.chaos;

public class ChaosFailureException extends RuntimeException {

    public ChaosFailureException() {
        super("Injected failure (chaos)");
    }
}
