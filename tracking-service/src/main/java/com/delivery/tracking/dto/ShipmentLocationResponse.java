package com.delivery.tracking.dto;

import com.delivery.tracking.entity.Location;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ShipmentLocationResponse(
        Long locationId,
        Long shipmentId,
        BigDecimal latitude,
        BigDecimal longitude,
        LocalDateTime recordedAt
) {

    public static ShipmentLocationResponse from(Location location) {
        return new ShipmentLocationResponse(
                location.getLocationId(),
                location.getShipmentId(),
                location.getLatitude(),
                location.getLongitude(),
                location.getRecordedAt()
        );
    }
}