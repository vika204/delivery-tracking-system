package com.delivery.tracking.service;

import com.delivery.tracking.dto.LiveLocationDecisionResult;
import com.delivery.tracking.dto.RoutePoint;
import com.delivery.tracking.entity.LiveLocationReceipt;
import com.delivery.tracking.entity.Location;
import com.delivery.tracking.repository.LiveLocationReceiptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

@Service
public class CourierLiveLocationService {

    private static final Logger log = LoggerFactory.getLogger(CourierLiveLocationService.class);
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private final LocationService locationService;
    private final LiveLocationReceiptRepository receiptRepository;
    private final TransactionTemplate transactionTemplate;
    private final double maxJumpMeters;
    private final double maxSpeedKmh;
    private final double minSpeedCheckDistanceMeters;
    private final Clock clock;

    public CourierLiveLocationService(
            LocationService locationService,
            LiveLocationReceiptRepository receiptRepository,
            TransactionTemplate transactionTemplate,
            @Value("${tracking.live.max-jump-meters:5000.0}") double maxJumpMeters,
            @Value("${tracking.live.max-speed-kmh:150.0}") double maxSpeedKmh,
            @Value("${tracking.live.min-speed-check-distance-meters:200.0}") double minSpeedCheckDistanceMeters
    ) {
        this.locationService = locationService;
        this.receiptRepository = receiptRepository;
        this.transactionTemplate = transactionTemplate;
        this.maxJumpMeters = maxJumpMeters;
        this.maxSpeedKmh = maxSpeedKmh;
        this.minSpeedCheckDistanceMeters = minSpeedCheckDistanceMeters;
        this.clock = Clock.systemUTC();
    }

    public LiveLocationDecisionResult processPoint(String pointId, long shipmentId, RoutePoint point) {
        Optional<LiveLocationDecisionResult> existing = findExisting(pointId, shipmentId);
        if (existing.isPresent()) {
            return existing.get();
        }

        Optional<Location> previousOpt = locationService.findLatestLocation(shipmentId);
        double distanceMeters = 0.0;
        double speedKmh = 0.0;

        if (previousOpt.isPresent()) {
            Location prev = previousOpt.get();
            distanceMeters = round1(haversineMeters(
                    prev.getLatitude().doubleValue(),
                    prev.getLongitude().doubleValue(),
                    point.latitude().doubleValue(),
                    point.longitude().doubleValue()
            ));

            long deltaMillis = Duration.between(prev.getRecordedAt(), point.recordedAt()).toMillis();
            if (deltaMillis > 0) {
                double seconds = deltaMillis / 1000.0;
                speedKmh = round1((distanceMeters / seconds) * 3.6);
            } else if (distanceMeters > minSpeedCheckDistanceMeters) {
                speedKmh = Double.POSITIVE_INFINITY;
            }

            String jumpReason = detectSuspiciousJump(distanceMeters, speedKmh, deltaMillis);
            if (jumpReason != null) {
                double reportedSpeed = Double.isInfinite(speedKmh) ? 9999.9 : speedKmh;
                log.warn("Suspicious courier location jump detected: pointId={}, shipmentId={}, distanceMeters={}, speedKmh={}, reason={}",
                        pointId, shipmentId, distanceMeters, reportedSpeed, jumpReason);
                return LiveLocationDecisionResult.suspiciousJump(
                        pointId, shipmentId, distanceMeters, reportedSpeed, jumpReason);
            }
        }

        final double finalDistance = distanceMeters;
        final double finalSpeed = Double.isInfinite(speedKmh) ? 0.0 : speedKmh;

        try {
            LiveLocationDecisionResult saved = transactionTemplate.execute(status ->
                    saveAcceptedPoint(pointId, shipmentId, point, finalDistance, finalSpeed));
            log.info("Live courier location accepted: pointId={}, shipmentId={}, locationId={}, distanceMeters={}, speedKmh={}",
                    pointId, shipmentId, saved.locationId(), saved.distanceMeters(), saved.speedKmh());
            return saved;
        } catch (DataIntegrityViolationException ex) {
            return findExisting(pointId, shipmentId).orElseThrow(() -> ex);
        }
    }

    private LiveLocationDecisionResult saveAcceptedPoint(
            String pointId,
            long shipmentId,
            RoutePoint point,
            double distanceMeters,
            double speedKmh
    ) {
        Location saved = locationService.createLocation(new Location(
                null,
                shipmentId,
                point.latitude(),
                point.longitude(),
                point.recordedAt()
        ));

        receiptRepository.saveAndFlush(new LiveLocationReceipt(
                pointId,
                shipmentId,
                saved.getLocationId(),
                distanceMeters,
                speedKmh,
                Instant.now(clock)
        ));

        return LiveLocationDecisionResult.accepted(
                pointId,
                shipmentId,
                saved.getLocationId(),
                distanceMeters,
                speedKmh,
                false
        );
    }

    private Optional<LiveLocationDecisionResult> findExisting(String pointId, long shipmentId) {
        return receiptRepository.findById(pointId).map(receipt -> {
            if (receipt.getShipmentId() != shipmentId) {
                log.warn("PointId reused for another shipment: pointId={}, expectedShipmentId={}, actualShipmentId={}",
                        pointId, receipt.getShipmentId(), shipmentId);
                return LiveLocationDecisionResult.rejected(
                        pointId,
                        shipmentId,
                        "pointId " + pointId + " is already used for another shipment"
                );
            }

            log.info("Duplicate live courier location ignored: pointId={}, shipmentId={}, locationId={}",
                    pointId, shipmentId, receipt.getLocationId());
            return LiveLocationDecisionResult.accepted(
                    receipt.getPointId(),
                    receipt.getShipmentId(),
                    receipt.getLocationId(),
                    receipt.getDistanceMeters(),
                    receipt.getSpeedKmh(),
                    true
            );
        });
    }

    private String detectSuspiciousJump(double distanceMeters, double speedKmh, long deltaMillis) {
        if (distanceMeters > maxJumpMeters) {
            double shownSpeed = Double.isInfinite(speedKmh) ? 9999.9 : speedKmh;
            return String.format(Locale.ROOT,
                    "suspicious location jump: %.1f m exceeds max %.1f m (speed %.1f km/h)",
                    distanceMeters, maxJumpMeters, shownSpeed);
        }
        if (distanceMeters > minSpeedCheckDistanceMeters && deltaMillis <= 0) {
            return String.format(Locale.ROOT,
                    "suspicious location jump: %.1f m with non-increasing timestamp",
                    distanceMeters);
        }
        if (distanceMeters > minSpeedCheckDistanceMeters && speedKmh > maxSpeedKmh) {
            return String.format(Locale.ROOT,
                    "suspicious location jump: speed %.1f km/h exceeds max %.1f km/h (distance %.1f m)",
                    speedKmh, maxSpeedKmh, distanceMeters);
        }
        return null;
    }

    static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
