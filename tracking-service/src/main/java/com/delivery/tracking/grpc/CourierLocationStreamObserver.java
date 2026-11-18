package com.delivery.tracking.grpc;

import com.delivery.tracking.api.ProtoTimestamps;
import com.delivery.tracking.dto.LiveLocationDecisionResult;
import com.delivery.tracking.service.CourierLiveLocationService;
import com.delivery.tracking.service.RoutePointValidator;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

class CourierLocationStreamObserver implements StreamObserver<CourierLocationReport> {

    static final int MAX_POINT_ID_LENGTH = 64;

    private static final Logger log = LoggerFactory.getLogger(CourierLocationStreamObserver.class);

    private final StreamObserver<CourierLocationDecision> responseObserver;
    private final CourierLiveLocationService liveLocationService;
    private final RoutePointValidator pointValidator;
    private final TrackingProtoMapper mapper;

    private int receivedCount;
    private boolean closed;

    CourierLocationStreamObserver(StreamObserver<CourierLocationDecision> responseObserver,
                                  CourierLiveLocationService liveLocationService,
                                  RoutePointValidator pointValidator,
                                  TrackingProtoMapper mapper) {
        this.responseObserver = responseObserver;
        this.liveLocationService = liveLocationService;
        this.pointValidator = pointValidator;
        this.mapper = mapper;
    }

    @Override
    public synchronized void onNext(CourierLocationReport report) {
        if (closed) {
            return;
        }
        receivedCount++;
        String pointId = report.getPointId().isBlank() ? "auto-" + UUID.randomUUID() : report.getPointId().trim();
        long shipmentId = report.getShipmentId();
        Instant recordedAt = report.hasRecordedAt() ? ProtoTimestamps.toInstant(report.getRecordedAt()) : null;

        LiveLocationDecisionResult decision;
        try {
            Optional<String> violation = findViolation(pointId, report, recordedAt);
            decision = violation.isPresent()
                    ? LiveLocationDecisionResult.rejected(pointId, shipmentId, violation.get())
                    : liveLocationService.processPoint(pointId, shipmentId,
                            mapper.toRoutePoint(report.getLatitude(), report.getLongitude(), recordedAt));
        } catch (RuntimeException ex) {
            closed = true;
            log.error("Live courier location processing failed: pointId={}, shipmentId={}", pointId, shipmentId, ex);
            responseObserver.onError(GrpcStatusMapper.toStatus(ex));
            return;
        }
        responseObserver.onNext(mapper.toProto(decision));
    }

    @Override
    public synchronized void onError(Throwable error) {
        closed = true;
        log.warn("Live courier location stream aborted by client: received={}, status={}",
                receivedCount, Status.fromThrowable(error).getCode());
    }

    @Override
    public synchronized void onCompleted() {
        if (closed) {
            return;
        }
        closed = true;
        log.info("Live courier location stream completed: received={}", receivedCount);
        responseObserver.onCompleted();
    }

    private Optional<String> findViolation(String pointId, CourierLocationReport report, Instant recordedAt) {
        if (pointId.length() > MAX_POINT_ID_LENGTH) {
            return Optional.of("pointId must be at most " + MAX_POINT_ID_LENGTH + " characters");
        }
        if (report.getShipmentId() <= 0) {
            return Optional.of("shipmentId must be positive");
        }
        return pointValidator.findViolation(report.getLatitude(), report.getLongitude(), recordedAt);
    }
}
