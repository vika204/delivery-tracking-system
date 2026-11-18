package com.delivery.tracking.grpc;

import com.delivery.tracking.dto.LiveLocationDecisionResult;
import com.delivery.tracking.dto.RoutePoint;
import com.delivery.tracking.service.CourierLiveLocationService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

class CourierLocationStreamObserver implements StreamObserver<CourierLocationReport> {

    static final int MAX_POINT_ID_LENGTH = 64;

    private static final Logger log = LoggerFactory.getLogger(CourierLocationStreamObserver.class);

    private final StreamObserver<CourierLocationDecision> responseObserver;
    private final CourierLiveLocationService liveLocationService;
    private final TrackingProtoMapper mapper;

    private int receivedCount;
    private boolean closed;

    CourierLocationStreamObserver(StreamObserver<CourierLocationDecision> responseObserver,
                                  CourierLiveLocationService liveLocationService,
                                  TrackingProtoMapper mapper) {
        this.responseObserver = responseObserver;
        this.liveLocationService = liveLocationService;
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

        LiveLocationDecisionResult decision;
        try {
            decision = liveLocationService.processPoint(pointId, shipmentId, toRoutePoint(pointId, report));
        } catch (IllegalArgumentException ex) {
            decision = LiveLocationDecisionResult.rejected(pointId, shipmentId, ex.getMessage());
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

    private RoutePoint toRoutePoint(String pointId, CourierLocationReport report) {
        if (pointId.length() > MAX_POINT_ID_LENGTH) {
            throw new IllegalArgumentException("pointId must be at most " + MAX_POINT_ID_LENGTH + " characters");
        }
        if (report.getShipmentId() <= 0) {
            throw new IllegalArgumentException("shipmentId must be positive");
        }
        return mapper.toRoutePoint(report.getLatitude(), report.getLongitude(),
                report.hasRecordedAt() ? report.getRecordedAt() : null);
    }
}
