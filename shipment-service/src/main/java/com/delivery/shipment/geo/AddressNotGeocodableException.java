package com.delivery.shipment.geo;

public class AddressNotGeocodableException extends RuntimeException {

    public AddressNotGeocodableException(String message) {
        super(message);
    }
}
