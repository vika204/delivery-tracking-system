package com.delivery.tracking.grpc;

import com.delivery.tracking.dto.RoutePoint;
import com.delivery.tracking.service.ShipmentRouteService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

class RouteUploadObserver implements StreamObserver<ShipmentRoutePoint> {

    static final int MAX_POINTS = 1000;
    static final int MAX_UPLOAD_ID_LENGTH = 64;

    private static final Logger log = LoggerFactory.getLogger(RouteUploadObserver.class);

    private final StreamObserver<ShipmentRouteSummary> responseObserver;
    private final ShipmentRouteService routeService;
    private final TrackingProtoMapper mapper;
    private final List<RoutePoint> validPoints = new ArrayList<>();

    private String uploadId;
    private long shipmentId;
    private int receivedCount;
    private boolean closed;

    RouteUploadObserver(StreamObserver<ShipmentRouteSummary> responseObserver,
                        ShipmentRouteService routeService,
                        TrackingProtoMapper mapper) {
        this.responseObserver = responseObserver;
        this.routeService = routeService;
        this.mapper = mapper;
    }

    @Override
    public synchronized void onNext(ShipmentRoutePoint point) {
        if (closed) {
            return;
        }
        String protocolError = validateStream(point);
        if (protocolError != null) {
            fail(Status.INVALID_ARGUMENT.withDescription(protocolError));
            return;
        }
        uploadId = point.getUploadId();
        shipmentId = point.getShipmentId();
        receivedCount++;

        try {
            validPoints.add(mapper.toRoutePoint(point.getLatitude(), point.getLongitude(),
                    point.hasRecordedAt() ? point.getRecordedAt() : null));
        } catch (IllegalArgumentException ex) {
            log.warn("Route point rejected: uploadId={}, shipmentId={}, index={}, reason={}",
                    uploadId, shipmentId, receivedCount - 1, ex.getMessage());
        }
    }

    @Override
    public synchronized void onError(Throwable error) {
        closed = true;
        validPoints.clear();
        log.warn("Route upload aborted by client: uploadId={}, shipmentId={}, receivedPoints={}, status={}",
                uploadId, shipmentId, receivedCount, Status.fromThrowable(error).getCode());
    }

    @Override
    public synchronized void onCompleted() {
        if (closed) {
            return;
        }
        if (receivedCount == 0) {
            fail(Status.INVALID_ARGUMENT.withDescription("route must contain at least one point"));
            return;
        }
        closed = true;
        try {
            responseObserver.onNext(mapper.toProto(routeService.recordRoute(uploadId, shipmentId, List.copyOf(validPoints), receivedCount)));
            responseObserver.onCompleted();
        } catch (RuntimeException ex) {
            log.error("Route upload failed: uploadId={}, shipmentId={}", uploadId, shipmentId, ex);
            responseObserver.onError(GrpcStatusMapper.toStatus(ex));
        } finally {
            validPoints.clear();
        }
    }

    private String validateStream(ShipmentRoutePoint point) {
        if (point.getUploadId().isBlank() || point.getUploadId().length() > MAX_UPLOAD_ID_LENGTH) {
            return "uploadId must be non-blank and at most " + MAX_UPLOAD_ID_LENGTH + " characters";
        }
        if (point.getShipmentId() <= 0) {
            return "shipmentId must be positive";
        }
        if (uploadId != null && (!uploadId.equals(point.getUploadId()) || shipmentId != point.getShipmentId())) {
            return "all points of one upload must have the same uploadId and shipmentId";
        }
        if (receivedCount >= MAX_POINTS) {
            return "route must contain at most " + MAX_POINTS + " points";
        }
        return null;
    }

    private void fail(Status status) {
        closed = true;
        validPoints.clear();
        log.warn("Route upload rejected: uploadId={}, shipmentId={}, description={}", uploadId, shipmentId, status.getDescription());
        responseObserver.onError(status.asRuntimeException());
    }
}
