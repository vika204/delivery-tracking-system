package com.delivery.tracking.grpc;

import com.delivery.tracking.live.CourierLiveLocationService;
import com.delivery.tracking.live.LiveLocationDecisionResult;
import com.delivery.tracking.route.RoutePoint;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

class CourierLocationStreamObserver implements StreamObserver<CourierLocationReport> {

    static final int MAX_POINT_ID_LENGTH = 64;
    static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(5);

    private static final Logger log = LoggerFactory.getLogger(CourierLocationStreamObserver.class);

    private final StreamObserver<CourierLocationDecision> responseObserver;
    private final CourierLiveLocationService liveLocationService;
    private final TrackingProtoMapper mapper;
    private final Clock clock;

    private int receivedCount;
    private int acceptedCount;
    private int rejectedCount;
    private int suspiciousCount;
    private boolean closed;

    CourierLocationStreamObserver(
            StreamObserver<CourierLocationDecision> responseObserver,
            CourierLiveLocationService liveLocationService,
            TrackingProtoMapper mapper,
            Clock clock
    ) {
        this.responseObserver = responseObserver;
        this.liveLocationService = liveLocationService;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public synchronized void onNext(CourierLocationReport report) {
        if (closed) {
            return;
        }

        if (receivedCount == 0) {
            log.info("Live courier location stream opened: firstShipmentId={}", report.getShipmentId());
        }
        receivedCount++;

        String rawPointId = report.getPointId().isBlank()
                ? "auto-" + UUID.randomUUID()
                : report.getPointId().trim();
        long shipmentId = report.getShipmentId();

        String validationError = validateReport(report, rawPointId);
        if (validationError != null) {
            rejectedCount++;
            log.warn("Live courier location rejected: pointId={}, shipmentId={}, index={}, reason={}",
                    rawPointId, shipmentId, receivedCount - 1, validationError);
            responseObserver.onNext(mapper.toProto(
                    LiveLocationDecisionResult.rejected(rawPointId, shipmentId, validationError)));
            return;
        }

        try {
            RoutePoint routePoint = mapper.toRoutePoint(report);
            LiveLocationDecisionResult decision = liveLocationService.processPoint(rawPointId, shipmentId, routePoint);

            switch (decision.type()) {
                case ACCEPTED -> acceptedCount++;
                case REJECTED -> rejectedCount++;
                case SUSPICIOUS_JUMP -> suspiciousCount++;
            }

            responseObserver.onNext(mapper.toProto(decision));
        } catch (RuntimeException ex) {
            closed = true;
            log.error("Live courier location processing failed: pointId={}, shipmentId={}",
                    rawPointId, shipmentId, ex);
            responseObserver.onError(GrpcStatusMapper.toStatus(ex));
        }
    }

    @Override
    public synchronized void onError(Throwable error) {
        closed = true;
        log.warn("Live courier location stream aborted by client: received={}, accepted={}, rejected={}, suspicious={}, status={}",
                receivedCount, acceptedCount, rejectedCount, suspiciousCount, Status.fromThrowable(error).getCode());
    }

    @Override
    public synchronized void onCompleted() {
        if (closed) {
            return;
        }
        closed = true;
        log.info("Live courier location stream completed: received={}, accepted={}, rejected={}, suspicious={}",
                receivedCount, acceptedCount, rejectedCount, suspiciousCount);
        responseObserver.onCompleted();
    }

    private String validateReport(CourierLocationReport report, String pointId) {
        if (pointId.length() > MAX_POINT_ID_LENGTH) {
            return "pointId must be at most " + MAX_POINT_ID_LENGTH + " characters";
        }
        if (report.getShipmentId() <= 0) {
            return "shipmentId must be positive";
        }
        if (!(report.getLatitude() >= -90 && report.getLatitude() <= 90)) {
            return "latitude must be between -90 and 90";
        }
        if (!(report.getLongitude() >= -180 && report.getLongitude() <= 180)) {
            return "longitude must be between -180 and 180";
        }
        if (!report.hasRecordedAt()) {
            return "recordedAt is required";
        }
        Instant recordedAt = Instant.ofEpochSecond(report.getRecordedAt().getSeconds(), report.getRecordedAt().getNanos());
        if (recordedAt.isAfter(Instant.now(clock).plus(MAX_CLOCK_SKEW))) {
            return "recordedAt must not be in the future";
        }
        return null;
    }
}
