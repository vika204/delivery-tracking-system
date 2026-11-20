package com.delivery.webui.grpc.dto;

import com.delivery.tracking.api.ProtoTimestamps;
import com.delivery.tracking.grpc.ShipmentLocationUpdate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

public record TrackingLocationEvent(
        long locationId,
        long shipmentId,
        double latitude,
        double longitude,
        LocalDateTime recordedAt
) {

    public static TrackingLocationEvent fromProto(long expectedShipmentId, ShipmentLocationUpdate update) {
        if (update == null || update.getLocationId() <= 0 || update.getShipmentId() != expectedShipmentId
                || !(update.getLatitude() >= -90 && update.getLatitude() <= 90)
                || !(update.getLongitude() >= -180 && update.getLongitude() <= 180)) {
            throw new IllegalArgumentException("Invalid shipment location update");
        }

        return new TrackingLocationEvent(
                update.getLocationId(),
                update.getShipmentId(),
                update.getLatitude(),
                update.getLongitude(),
                LocalDateTime.ofInstant(ProtoTimestamps.toInstant(update.getRecordedAt()), ZoneOffset.UTC)
        );
    }
}
