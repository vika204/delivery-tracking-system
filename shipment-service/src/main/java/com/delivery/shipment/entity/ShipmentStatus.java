package com.delivery.shipment.entity;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum ShipmentStatus {
    WAITING_FOR_COURIER,
    ASSIGNED,
    TO_PICKUP,
    IN_TRANSIT,
    DELIVERED,
    CANCELLED;

    public static final Set<ShipmentStatus> ACTIVE =
            Collections.unmodifiableSet(EnumSet.of(WAITING_FOR_COURIER, ASSIGNED, TO_PICKUP, IN_TRANSIT));

    public static final Set<ShipmentStatus> BEFORE_PICKUP =
            Collections.unmodifiableSet(EnumSet.of(WAITING_FOR_COURIER, ASSIGNED, TO_PICKUP));

    public boolean isTerminal() {
        return this == DELIVERED || this == CANCELLED;
    }
}
