package com.delivery.shipment.geo;

public class GeoapifyUnavailableException extends RuntimeException {

    public GeoapifyUnavailableException() {
        super("Address and driving distance calculation is temporarily unavailable. Please try again later.");
    }
}
