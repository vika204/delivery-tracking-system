package com.delivery.tracking.exception;

/** Точку не збережено: некоректні дані (400) або підозрілий стрибок позиції (422). */
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
