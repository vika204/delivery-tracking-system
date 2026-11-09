package com.delivery.tracking.grpc;

import com.delivery.tracking.event.LocationSavedEvent;
import com.google.protobuf.Timestamp;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;

@Component
public class TrackingProtoMapper {

    private final ZoneId recordedAtZone;

    public TrackingProtoMapper(@Value("${tracking.recorded-at-zone:UTC}") String zone) {
        this.recordedAtZone = ZoneId.of(zone);
    }

    public ShipmentLocationUpdate toProto(LocationSavedEvent event) {
        Instant instant = event.recordedAt()
                .atZone(recordedAtZone)
                .toInstant();

        Timestamp timestamp = Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();

        return ShipmentLocationUpdate.newBuilder()
                .setLocationId(event.locationId())
                .setShipmentId(event.shipmentId())
                .setLatitude(event.latitude().doubleValue())
                .setLongitude(event.longitude().doubleValue())
                .setRecordedAt(timestamp)
                .build();
    }
}