package com.delivery.tracking.service;

import com.delivery.tracking.dto.LiveLocationDecisionResult;
import com.delivery.tracking.dto.RecordLocationRequest;
import com.delivery.tracking.dto.RoutePoint;
import com.delivery.tracking.dto.ShipmentLocationResponse;
import com.delivery.tracking.exception.LocationRejectedException;
import com.delivery.tracking.exception.LocationRejectedException.Reason;
import com.delivery.tracking.repository.TrackingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

@Service
public class LocationRecordingService {

    private final RoutePointValidator validator;
    private final CourierLiveLocationService liveLocationService;
    private final TrackingRepository trackingRepository;
    private final ZoneId recordedAtZone;

    public LocationRecordingService(RoutePointValidator validator,
                                    CourierLiveLocationService liveLocationService,
                                    TrackingRepository trackingRepository,
                                    @Value("${tracking.recorded-at-zone:UTC}") String recordedAtZone) {
        this.validator = validator;
        this.liveLocationService = liveLocationService;
        this.trackingRepository = trackingRepository;
        this.recordedAtZone = ZoneId.of(recordedAtZone);
    }

    public Recorded recordLocation(long shipmentId, RecordLocationRequest request) {
        Optional<Recorded> replay = replay(shipmentId, request);
        if (replay.isPresent()) {
            return replay.get();
        }
        validator.findViolation(request.latitude().doubleValue(), request.longitude().doubleValue(), request.recordedAt())
                .ifPresent(violation -> {
                    throw new LocationRejectedException(Reason.INVALID, violation);
                });

        LocalDateTime recordedAt = LocalDateTime.ofInstant(request.recordedAt(), recordedAtZone);
        LiveLocationDecisionResult decision;
        try {
            decision = liveLocationService.processPoint(
                    shipmentId, new RoutePoint(request.latitude(), request.longitude(), recordedAt), request.pointId());
        } catch (DataIntegrityViolationException concurrentDuplicate) {
            return replay(shipmentId, request).orElseThrow(() -> concurrentDuplicate);
        }
        if (!decision.saved()) {
            throw new LocationRejectedException(Reason.SUSPICIOUS_JUMP, decision.reason());
        }
        return new Recorded(new ShipmentLocationResponse(
                decision.locationId(), shipmentId, request.latitude(), request.longitude(), recordedAt), false);
    }

    private Optional<Recorded> replay(long shipmentId, RecordLocationRequest request) {
        if (request == null || request.pointId() == null) {
            return Optional.empty();
        }
        return trackingRepository.findByShipmentIdAndClientPointId(shipmentId, request.pointId())
                .map(location -> new Recorded(ShipmentLocationResponse.from(location), true));
    }

    public record Recorded(ShipmentLocationResponse location, boolean replayed) {
    }
}
