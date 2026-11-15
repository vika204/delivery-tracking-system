package com.delivery.tracking.grpc;

import com.delivery.tracking.route.RoutePoint;
import com.delivery.tracking.route.RouteUploadResult;
import com.delivery.tracking.route.ShipmentRouteService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Серверна частина Client Streaming {@code RecordShipmentRoute}.
 * <p>
 * Точки лише валідуються й накопичуються в пам'яті; у БД вони потрапляють однією транзакцією
 * після {@code onCompleted()} клієнта. Тому обрив потоку посередині не залишає часткового маршруту,
 * а повторна спроба (gRPC retry надсилає всі буферизовані повідомлення заново) починає з чистого аркуша.
 */
class RouteUploadObserver implements StreamObserver<ShipmentRoutePoint> {

    static final int MAX_POINTS = 1000;
    static final int MAX_UPLOAD_ID_LENGTH = 64;

    private static final Logger log = LoggerFactory.getLogger(RouteUploadObserver.class);

    private final StreamObserver<ShipmentRouteSummary> responseObserver;
    private final ShipmentRouteService routeService;
    private final TrackingProtoMapper mapper;
    private final List<RoutePoint> points = new ArrayList<>();

    private String uploadId;
    private long shipmentId;
    private boolean closed;

    RouteUploadObserver(
            StreamObserver<ShipmentRouteSummary> responseObserver,
            ShipmentRouteService routeService,
            TrackingProtoMapper mapper
    ) {
        this.responseObserver = responseObserver;
        this.routeService = routeService;
        this.mapper = mapper;
    }

    @Override
    public synchronized void onNext(ShipmentRoutePoint point) {
        if (closed) {
            return;
        }

        String error = validate(point);
        if (error != null) {
            fail(Status.INVALID_ARGUMENT.withDescription(error));
            return;
        }

        if (uploadId == null) {
            uploadId = point.getUploadId();
            shipmentId = point.getShipmentId();
            log.info("Route upload started: uploadId={}, shipmentId={}", uploadId, shipmentId);
        }

        points.add(mapper.toRoutePoint(point));
    }

    @Override
    public synchronized void onError(Throwable error) {
        closed = true;
        log.warn("Route upload aborted by client: uploadId={}, shipmentId={}, receivedPoints={}, status={}",
                uploadId, shipmentId, points.size(), Status.fromThrowable(error).getCode());
        points.clear();
    }

    @Override
    public synchronized void onCompleted() {
        if (closed) {
            return;
        }

        if (points.isEmpty()) {
            fail(Status.INVALID_ARGUMENT.withDescription("route must contain at least one point"));
            return;
        }

        closed = true;

        try {
            RouteUploadResult result = routeService.recordRoute(uploadId, shipmentId, List.copyOf(points));
            responseObserver.onNext(mapper.toProto(result));
            responseObserver.onCompleted();
        } catch (ShipmentRouteService.UploadIdConflictException ex) {
            responseObserver.onError(Status.ALREADY_EXISTS.withDescription(ex.getMessage()).asRuntimeException());
        } catch (RuntimeException ex) {
            log.error("Route upload failed: uploadId={}, shipmentId={}", uploadId, shipmentId, ex);
            responseObserver.onError(GrpcStatusMapper.toStatus(ex));
        } finally {
            points.clear();
        }
    }

    private String validate(ShipmentRoutePoint point) {
        if (point.getUploadId().isBlank() || point.getUploadId().length() > MAX_UPLOAD_ID_LENGTH) {
            return "uploadId must be non-blank and at most " + MAX_UPLOAD_ID_LENGTH + " characters";
        }
        if (point.getShipmentId() <= 0) {
            return "shipmentId must be positive";
        }
        if (uploadId != null && (!uploadId.equals(point.getUploadId()) || shipmentId != point.getShipmentId())) {
            return "all points of one upload must have the same uploadId and shipmentId";
        }
        if (points.size() >= MAX_POINTS) {
            return "route must contain at most " + MAX_POINTS + " points";
        }
        if (!(point.getLatitude() >= -90 && point.getLatitude() <= 90)) {
            return "latitude must be between -90 and 90";
        }
        if (!(point.getLongitude() >= -180 && point.getLongitude() <= 180)) {
            return "longitude must be between -180 and 180";
        }
        if (!point.hasRecordedAt()) {
            return "recordedAt is required";
        }
        return null;
    }

    private void fail(Status status) {
        closed = true;
        points.clear();
        log.warn("Route upload rejected: uploadId={}, shipmentId={}, status={}, description={}",
                uploadId, shipmentId, status.getCode(), status.getDescription());
        responseObserver.onError(status.asRuntimeException());
    }
}
