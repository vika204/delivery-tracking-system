package com.delivery.shipment.entity;

import java.util.Set;

public enum StatusGroup {
    ACTIVE(ShipmentStatus.ACTIVE),
    COMPLETED(Set.of(ShipmentStatus.DELIVERED)),
    CANCELLED(Set.of(ShipmentStatus.CANCELLED));

    private final Set<ShipmentStatus> statuses;

    StatusGroup(Set<ShipmentStatus> statuses) {
        this.statuses = statuses;
    }

    public Set<ShipmentStatus> statuses() {
        return statuses;
    }
}
