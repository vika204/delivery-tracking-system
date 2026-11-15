package com.delivery.shipment.dto;

final class PhoneNumber {

    static final String PATTERN = "\\+?[0-9][0-9 ()-]{8,18}[0-9]";
    static final String MESSAGE = "must be a valid phone number";

    private PhoneNumber() {
    }
}
