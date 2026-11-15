package com.delivery.shipment.dto;

import com.delivery.shipment.entity.ActorType;
import com.delivery.shipment.entity.ShipmentStatus;
import com.delivery.shipment.entity.ShipmentStatusHistory;

import java.time.Instant;

public record ShipmentHistoryEntryResponse(
        ShipmentStatus fromStatus,
        ShipmentStatus toStatus,
        Instant changedAt,
        ActorType actorType,
        Long actorId,
        String reason
) {

    public static ShipmentHistoryEntryResponse from(ShipmentStatusHistory entry) {
        return new ShipmentHistoryEntryResponse(
                entry.getFromStatus(),
                entry.getToStatus(),
                entry.getChangedAt(),
                entry.getActorType(),
                entry.getActorId(),
                entry.getReason()
        );
    }
}
