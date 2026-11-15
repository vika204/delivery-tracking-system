package com.delivery.tracking.grpc;

import com.delivery.tracking.route.RoutePoint;
import com.delivery.tracking.route.RouteUploadResult;
import com.delivery.tracking.route.ShipmentRouteService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Серверна частина Client Streaming {@code RecordShipmentRoute}.
 * <p>
 * Кожна отримана координата перевіряється окремо: некоректні відхиляються й рахуються,
 * коректні накопичуються. У БД вони потрапляють однією транзакцією після {@code onCompleted()}
 * клієнта, тому обрив потоку посередині не залишає часткового маршруту, а повторна спроба
 * (gRPC retry надсилає всі буферизовані повідомлення заново) починає з чистого аркуша.
 * <p>
 * Увесь потік відхиляється з {@code INVALID_ARGUMENT} лише при порушенні протоколу:
 * порожній/різний upload_id, некоректний або різний shipment_id, перевищення ліміту точок.
 */
class RouteUploadObserver implements StreamObserver<ShipmentRoutePoint> {

    static final int MAX_POINTS = 1000;
    static final int MAX_UPLOAD_ID_LENGTH = 64;
    static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(5);

    private static final Logger log = LoggerFactory.getLogger(RouteUploadObserver.class);

    private final StreamObserver<ShipmentRouteSummary> responseObserver;
    private final ShipmentRouteService routeService;
    private final TrackingProtoMapper mapper;
    private final Clock clock;
    private final List<RoutePoint> validPoints = new ArrayList<>();

    private String uploadId;
    private long shipmentId;
    private int receivedCount;
    private boolean closed;

    RouteUploadObserver(
            StreamObserver<ShipmentRouteSummary> responseObserver,
            ShipmentRouteService routeService,
            TrackingProtoMapper mapper,
            Clock clock
    ) {
        this.responseObserver = responseObserver;
        this.routeService = routeService;
        this.mapper = mapper;
        this.clock = clock;
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

        if (uploadId == null) {
            uploadId = point.getUploadId();
            shipmentId = point.getShipmentId();
            log.info("Route upload started: uploadId={}, shipmentId={}", uploadId, shipmentId);
        }

        receivedCount++;

        String pointError = validatePoint(point);
        if (pointError != null) {
            log.warn("Route point rejected: uploadId={}, shipmentId={}, index={}, reason={}",
                    uploadId, shipmentId, receivedCount - 1, pointError);
            return;
        }

        validPoints.add(mapper.toRoutePoint(point));
    }

    @Override
    public synchronized void onError(Throwable error) {
        closed = true;
        log.warn("Route upload aborted by client: uploadId={}, shipmentId={}, receivedPoints={}, status={}",
                uploadId, shipmentId, receivedCount, Status.fromThrowable(error).getCode());
        validPoints.clear();
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
            RouteUploadResult result = routeService.recordRoute(uploadId, shipmentId, List.copyOf(validPoints), receivedCount);
            responseObserver.onNext(mapper.toProto(result));
            responseObserver.onCompleted();
        } catch (ShipmentRouteService.UploadIdConflictException ex) {
            responseObserver.onError(Status.ALREADY_EXISTS.withDescription(ex.getMessage()).asRuntimeException());
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

    private String validatePoint(ShipmentRoutePoint point) {
        if (!(point.getLatitude() >= -90 && point.getLatitude() <= 90)) {
            return "latitude must be between -90 and 90";
        }
        if (!(point.getLongitude() >= -180 && point.getLongitude() <= 180)) {
            return "longitude must be between -180 and 180";
        }
        if (!point.hasRecordedAt()) {
            return "recordedAt is required";
        }
        Instant recordedAt = Instant.ofEpochSecond(point.getRecordedAt().getSeconds(), point.getRecordedAt().getNanos());
        if (recordedAt.isAfter(Instant.now(clock).plus(MAX_CLOCK_SKEW))) {
            return "recordedAt must not be in the future";
        }
        return null;
    }

    private void fail(Status status) {
        closed = true;
        validPoints.clear();
        log.warn("Route upload rejected: uploadId={}, shipmentId={}, status={}, description={}",
                uploadId, shipmentId, status.getCode(), status.getDescription());
        responseObserver.onError(status.asRuntimeException());
    }
}
