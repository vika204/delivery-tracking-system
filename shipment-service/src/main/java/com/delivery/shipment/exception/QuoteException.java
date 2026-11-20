package com.delivery.shipment.exception;

import java.util.UUID;

public class QuoteException extends RuntimeException {

    public enum Reason {
        NOT_FOUND,
        EXPIRED,
        ALREADY_USED
    }

    private static final String PREFIX = "Price quote ";

    private final Reason reason;

    public QuoteException(Reason reason, UUID quoteId) {
        super(switch (reason) {
            case NOT_FOUND -> PREFIX + quoteId + " not found";
            case EXPIRED -> PREFIX + quoteId + " has expired, request a new one";
            case ALREADY_USED -> PREFIX + quoteId + " is already used by another shipment";
        });
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
