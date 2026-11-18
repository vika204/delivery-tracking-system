package com.delivery.tracking.grpc;

import com.delivery.tracking.entity.Location;
import com.delivery.tracking.event.LocationSavedEvent;
import com.delivery.tracking.dto.LiveLocationDecisionResult;
import com.delivery.tracking.dto.RoutePoint;
import com.delivery.tracking.dto.RouteUploadResult;
import com.google.protobuf.Timestamp;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

@Component
public class TrackingProtoMapper {

    private static final int COORDINATE_SCALE = 6;
    private static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(5);

    private final ZoneId recordedAtZone;
    private final Clock clock = Clock.systemUTC();

    public TrackingProtoMapper(@Value("${tracking.recorded-at-zone:UTC}") String zone) {
        this.recordedAtZone = ZoneId.of(zone);
    }

    public ShipmentLocationUpdate toProto(LocationSavedEvent event) {
        return toProto(event.locationId(), event.shipmentId(), event.latitude(), event.longitude(), event.recordedAt());
    }

    public ShipmentDeliveryInfo toDeliveryInfo(long shipmentId, String status, Optional<Location> latestLocation) {
        ShipmentDeliveryInfo.Builder info = ShipmentDeliveryInfo.newBuilder()
                .setShipmentId(shipmentId)
                .setStatus(status);

        latestLocation.ifPresent(location -> info
                .setHasLocation(true)
                .setLatitude(location.getLatitude().doubleValue())
                .setLongitude(location.getLongitude().doubleValue())
                .setRecordedAt(toTimestamp(location.getRecordedAt())));

        return info.build();
    }

    /**
     * @throws IllegalArgumentException із причиною відхилення, якщо точка некоректна
     */
    public RoutePoint toRoutePoint(double latitude, double longitude, Timestamp recordedAt) {
        if (!(latitude >= -90 && latitude <= 90)) {
            throw new IllegalArgumentException("latitude must be between -90 and 90");
        }
        if (!(longitude >= -180 && longitude <= 180)) {
            throw new IllegalArgumentException("longitude must be between -180 and 180");
        }
        if (recordedAt == null) {
            throw new IllegalArgumentException("recordedAt is required");
        }
        Instant instant = Instant.ofEpochSecond(recordedAt.getSeconds(), recordedAt.getNanos());
        if (instant.isAfter(Instant.now(clock).plus(MAX_CLOCK_SKEW))) {
            throw new IllegalArgumentException("recordedAt must not be in the future");
        }
        return new RoutePoint(
                BigDecimal.valueOf(latitude).setScale(COORDINATE_SCALE, RoundingMode.HALF_UP),
                BigDecimal.valueOf(longitude).setScale(COORDINATE_SCALE, RoundingMode.HALF_UP),
                LocalDateTime.ofInstant(instant, recordedAtZone)
        );
    }

    public ShipmentRouteSummary toProto(RouteUploadResult result) {
        return ShipmentRouteSummary.newBuilder()
                .setUploadId(result.uploadId())
                .setShipmentId(result.shipmentId())
                .setReceivedCount(result.receivedCount())
                .setSavedCount(result.savedCount())
                .setRejectedCount(result.rejectedCount())
                .setFirstLocationId(result.firstLocationId() == null ? 0 : result.firstLocationId())
                .setLastLocationId(result.lastLocationId() == null ? 0 : result.lastLocationId())
                .setDuplicate(result.duplicate())
                .build();
    }

    public CourierLocationDecision toProto(LiveLocationDecisionResult result) {
        CourierLocationStatus status = switch (result.type()) {
            case ACCEPTED -> CourierLocationStatus.COURIER_LOCATION_STATUS_ACCEPTED;
            case REJECTED -> CourierLocationStatus.COURIER_LOCATION_STATUS_REJECTED;
            case SUSPICIOUS_JUMP -> CourierLocationStatus.COURIER_LOCATION_STATUS_SUSPICIOUS_JUMP;
        };

        return CourierLocationDecision.newBuilder()
                .setPointId(result.pointId() == null ? "" : result.pointId())
                .setShipmentId(result.shipmentId())
                .setStatus(status)
                .setSaved(result.saved())
                .setLocationId(result.locationId() == null ? 0 : result.locationId())
                .setReason(result.reason() == null ? "" : result.reason())
                .setDistanceMeters(result.distanceMeters())
                .setSpeedKmh(result.speedKmh())
                .setDuplicate(result.duplicate())
                .build();
    }

    private ShipmentLocationUpdate toProto(
            Long locationId,
            Long shipmentId,
            BigDecimal latitude,
            BigDecimal longitude,
            LocalDateTime recordedAt
    ) {
        return ShipmentLocationUpdate.newBuilder()
                .setLocationId(locationId)
                .setShipmentId(shipmentId)
                .setLatitude(latitude.doubleValue())
                .setLongitude(longitude.doubleValue())
                .setRecordedAt(toTimestamp(recordedAt))
                .build();
    }

    private Timestamp toTimestamp(LocalDateTime recordedAt) {
        Instant instant = recordedAt
                .atZone(recordedAtZone)
                .toInstant();

        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }
}
