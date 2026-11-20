package com.delivery.tracking.service;

import com.delivery.tracking.dto.LiveLocationDecisionResult;
import com.delivery.tracking.dto.RoutePoint;
import com.delivery.tracking.entity.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class CourierLiveLocationService {

    private static final Logger log = LoggerFactory.getLogger(CourierLiveLocationService.class);
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private final LocationService locationService;
    private final double maxJumpMeters;

    public CourierLiveLocationService(
            LocationService locationService,
            @Value("${tracking.live.max-jump-meters:5000.0}") double maxJumpMeters
    ) {
        this.locationService = locationService;
        this.maxJumpMeters = maxJumpMeters;
    }

    public LiveLocationDecisionResult processPoint(long shipmentId, RoutePoint point) {
        return processPoint(shipmentId, point, null);
    }

    public LiveLocationDecisionResult processPoint(long shipmentId, RoutePoint point, UUID clientPointId) {
        Optional<Location> previousOpt = locationService.findLatestLocation(shipmentId);
        double distanceMeters = 0.0;

        if (previousOpt.isPresent()) {
            Location prev = previousOpt.get();
            distanceMeters = round1(haversineMeters(
                    prev.getLatitude().doubleValue(),
                    prev.getLongitude().doubleValue(),
                    point.latitude().doubleValue(),
                    point.longitude().doubleValue()
            ));

            if (distanceMeters > maxJumpMeters) {
                String reason = String.format(Locale.ROOT,
                        "suspicious location jump: %.1f m exceeds max %.1f m",
                        distanceMeters, maxJumpMeters);
                log.warn("Suspicious courier location jump: shipmentId={}, distanceMeters={}", shipmentId, distanceMeters);
                return LiveLocationDecisionResult.suspiciousJump(shipmentId, distanceMeters, reason);
            }
        }

        Location saved = locationService.createLocation(new Location(
                null,
                shipmentId,
                point.latitude(),
                point.longitude(),
                point.recordedAt(),
                clientPointId
        ));

        log.info("Live courier location accepted: shipmentId={}, locationId={}, distanceMeters={}",
                shipmentId, saved.getLocationId(), distanceMeters);
        return LiveLocationDecisionResult.accepted(shipmentId, saved.getLocationId(), distanceMeters);
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
