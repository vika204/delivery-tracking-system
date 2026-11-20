package com.delivery.shipment.exception;

public class AddressNotGeocodableException extends RuntimeException {

    public AddressNotGeocodableException(String message) {
        super(message);
    }
}
