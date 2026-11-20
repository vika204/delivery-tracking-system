package com.delivery.tracking.exception;

public class LocationRejectedException extends RuntimeException {

    public enum Reason {
        INVALID,
        SUSPICIOUS_JUMP
    }

    private final Reason reason;

    public LocationRejectedException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
