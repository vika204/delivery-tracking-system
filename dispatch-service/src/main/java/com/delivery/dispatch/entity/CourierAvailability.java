package com.delivery.dispatch.entity;

/**
 * Стан кур'єра для Dashboard. Похідний від перемикача «на лінії» та наявності активної доставки:
 * активна доставка має пріоритет, тож BUSY не залежить від положення перемикача.
 */
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
