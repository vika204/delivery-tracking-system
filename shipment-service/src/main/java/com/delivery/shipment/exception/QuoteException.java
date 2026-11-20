package com.delivery.shipment.exception;

import java.util.UUID;

public class QuoteException extends RuntimeException {

    public enum Reason {
        NOT_FOUND,
        EXPIRED,
        ALREADY_USED
    }

    private final Reason reason;

    public QuoteException(Reason reason, UUID quoteId) {
        super(switch (reason) {
            case NOT_FOUND -> "Price quote " + quoteId + " not found";
            case EXPIRED -> "Price quote " + quoteId + " has expired, request a new one";
            case ALREADY_USED -> "Price quote " + quoteId + " is already used by another shipment";
        });
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
