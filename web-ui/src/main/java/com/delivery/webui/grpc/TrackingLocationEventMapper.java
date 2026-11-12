package com.delivery.webui.grpc;

import com.delivery.tracking.grpc.ShipmentLocationUpdate;
import com.delivery.webui.grpc.dto.TrackingLocationEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
@ConditionalOnProperty(
        prefix = "tracking.stream",
        name = "enabled",
        havingValue = "true"
)
public class TrackingLocationEventMapper {

    public TrackingLocationEvent toEvent(
            long expectedShipmentId,
            ShipmentLocationUpdate update
    ) {
        if (update == null
                || update.getLocationId() <= 0
                || update.getShipmentId() != expectedShipmentId
                || !isValidLatitude(update.getLatitude())
                || !isValidLongitude(update.getLongitude())) {
            throw new IllegalArgumentException("Invalid shipment location update");
        }

        Instant recordedAt = Instant.ofEpochSecond(
                update.getRecordedAt().getSeconds(),
                update.getRecordedAt().getNanos()
        );

        return new TrackingLocationEvent(
                update.getLocationId(),
                update.getShipmentId(),
                update.getLatitude(),
                update.getLongitude(),
                LocalDateTime.ofInstant(recordedAt, ZoneOffset.UTC)
        );
    }

    private boolean isValidLatitude(double latitude) {
        return Double.isFinite(latitude)
                && latitude >= -90
                && latitude <= 90;
    }

    private boolean isValidLongitude(double longitude) {
        return Double.isFinite(longitude)
                && longitude >= -180
                && longitude <= 180;
    }
}
