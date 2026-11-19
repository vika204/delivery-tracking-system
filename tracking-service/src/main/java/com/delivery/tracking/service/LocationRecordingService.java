package com.delivery.tracking.service;

import com.delivery.tracking.dto.LiveLocationDecisionResult;
import com.delivery.tracking.dto.RecordLocationRequest;
import com.delivery.tracking.dto.RoutePoint;
import com.delivery.tracking.dto.ShipmentLocationResponse;
import com.delivery.tracking.exception.LocationRejectedException;
import com.delivery.tracking.exception.LocationRejectedException.Reason;
import com.delivery.tracking.repository.TrackingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Запис GPS-точки через REST. Ті самі правила, що й для gRPC-потоків кур'єра:
 * {@link RoutePointValidator} і перевірка підозрілого стрибка в {@link CourierLiveLocationService}.
 * Збережена точка публікується підписникам gRPC Server Streaming після коміту.
 */
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

    public ShipmentLocationResponse record(long shipmentId, RecordLocationRequest request) {
        if (shipmentId <= 0) {
            throw new LocationRejectedException(Reason.INVALID, "shipmentId must be positive");
        }
        if (request == null || request.latitude() == null || request.longitude() == null) {
            throw new LocationRejectedException(Reason.INVALID, "latitude and longitude are required");
        }
        validator.findViolation(request.latitude().doubleValue(), request.longitude().doubleValue(), request.recordedAt())
                .ifPresent(violation -> {
                    throw new LocationRejectedException(Reason.INVALID, violation);
                });

        LiveLocationDecisionResult decision = liveLocationService.processPoint(shipmentId, new RoutePoint(
                request.latitude(),
                request.longitude(),
                LocalDateTime.ofInstant(request.recordedAt(), recordedAtZone)));
        if (!decision.saved()) {
            throw new LocationRejectedException(Reason.SUSPICIOUS_JUMP, decision.reason());
        }
        return trackingRepository.findById(decision.locationId())
                .map(ShipmentLocationResponse::from)
                .orElseThrow();
    }
}
