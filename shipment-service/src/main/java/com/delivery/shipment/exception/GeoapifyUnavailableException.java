package com.delivery.shipment.exception;

public class GeoapifyUnavailableException extends RuntimeException {

    public GeoapifyUnavailableException() {
        super("Address and driving distance calculation is temporarily unavailable. Please try again later.");
    }
}
