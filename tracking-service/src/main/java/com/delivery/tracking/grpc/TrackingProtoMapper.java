package com.delivery.tracking.grpc;

import com.delivery.tracking.entity.Location;
import com.delivery.tracking.event.LocationSavedEvent;
import com.google.protobuf.Timestamp;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
public class TrackingProtoMapper {

    private final ZoneId recordedAtZone;

    public TrackingProtoMapper(@Value("${tracking.recorded-at-zone:UTC}") String zone) {
        this.recordedAtZone = ZoneId.of(zone);
    }

    public ShipmentLocationUpdate toProto(LocationSavedEvent event) {
        return toProto(event.locationId(), event.shipmentId(), event.latitude(), event.longitude(), event.recordedAt());
    }

    public ShipmentLocationUpdate toProto(Location location) {
        return toProto(
                location.getLocationId(),
                location.getShipmentId(),
                location.getLatitude(),
                location.getLongitude(),
                location.getRecordedAt()
        );
    }

    private ShipmentLocationUpdate toProto(
            Long locationId,
            Long shipmentId,
            BigDecimal latitude,
            BigDecimal longitude,
            LocalDateTime recordedAt
    ) {
        Instant instant = recordedAt
                .atZone(recordedAtZone)
                .toInstant();

        Timestamp timestamp = Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();

        return ShipmentLocationUpdate.newBuilder()
                .setLocationId(locationId)
                .setShipmentId(shipmentId)
                .setLatitude(latitude.doubleValue())
                .setLongitude(longitude.doubleValue())
                .setRecordedAt(timestamp)
                .build();
    }
}
