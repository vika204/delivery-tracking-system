package com.delivery.tracking.grpc;

import com.delivery.tracking.entity.Location;
import com.delivery.tracking.event.LocationSavedEvent;
import com.delivery.tracking.live.LiveLocationDecisionResult;
import com.delivery.tracking.route.RoutePoint;
import com.delivery.tracking.route.RouteUploadResult;
import com.google.protobuf.Timestamp;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

@Component
public class TrackingProtoMapper {

    // Відповідає precision = 9, scale = 6 у Location.
    private static final int COORDINATE_SCALE = 6;

    private final ZoneId recordedAtZone;

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

    public RoutePoint toRoutePoint(ShipmentRoutePoint point) {
        Instant instant = Instant.ofEpochSecond(point.getRecordedAt().getSeconds(), point.getRecordedAt().getNanos());

        return new RoutePoint(
                BigDecimal.valueOf(point.getLatitude()).setScale(COORDINATE_SCALE, RoundingMode.HALF_UP),
                BigDecimal.valueOf(point.getLongitude()).setScale(COORDINATE_SCALE, RoundingMode.HALF_UP),
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

    public RoutePoint toRoutePoint(CourierLocationReport report) {
        Instant instant = Instant.ofEpochSecond(report.getRecordedAt().getSeconds(), report.getRecordedAt().getNanos());

        return new RoutePoint(
                BigDecimal.valueOf(report.getLatitude()).setScale(COORDINATE_SCALE, RoundingMode.HALF_UP),
                BigDecimal.valueOf(report.getLongitude()).setScale(COORDINATE_SCALE, RoundingMode.HALF_UP),
                LocalDateTime.ofInstant(instant, recordedAtZone)
        );
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
