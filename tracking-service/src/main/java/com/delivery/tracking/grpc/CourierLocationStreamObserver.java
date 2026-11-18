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

class CourierLocationStreamObserver implements StreamObserver<CourierLocationReport> {

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
        long shipmentId = report.getShipmentId();
        Instant recordedAt = report.hasRecordedAt() ? ProtoTimestamps.toInstant(report.getRecordedAt()) : null;

        LiveLocationDecisionResult decision;
        try {
            Optional<String> violation = findViolation(shipmentId, report, recordedAt);
            decision = violation.isPresent()
                    ? LiveLocationDecisionResult.rejected(shipmentId, violation.get())
                    : liveLocationService.processPoint(shipmentId,
                            mapper.toRoutePoint(report.getLatitude(), report.getLongitude(), recordedAt));
        } catch (RuntimeException ex) {
            closed = true;
            log.error("Live courier location processing failed: shipmentId={}", shipmentId, ex);
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

    private Optional<String> findViolation(long shipmentId, CourierLocationReport report, Instant recordedAt) {
        if (shipmentId <= 0) {
            return Optional.of("shipmentId must be positive");
        }
        return pointValidator.findViolation(report.getLatitude(), report.getLongitude(), recordedAt);
    }
}
