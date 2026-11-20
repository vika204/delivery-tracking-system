package com.delivery.dispatch.exception;

public class OfferNoLongerAvailableException extends DispatchConflictException {
    public OfferNoLongerAvailableException(String message) {
        super(message);
    }
}
