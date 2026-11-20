package com.delivery.dispatch.entity;

public enum CourierAvailability {
    AVAILABLE,
    BUSY,
    UNAVAILABLE;

    public static CourierAvailability of(boolean online, boolean hasActiveDelivery) {
        if (hasActiveDelivery) {
            return BUSY;
        }
        return online ? AVAILABLE : UNAVAILABLE;
    }
}
