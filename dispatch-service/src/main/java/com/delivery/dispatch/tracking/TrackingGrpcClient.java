package com.delivery.dispatch.tracking;

import com.delivery.dispatch.tracking.dto.LatestLocationResponse;
import com.delivery.dispatch.tracking.dto.RoutePointRequest;
import com.delivery.dispatch.tracking.dto.RouteUploadResponse;
import com.delivery.tracking.grpc.GetLatestShipmentLocationRequest;
import com.delivery.tracking.grpc.ShipmentLocationUpdate;
import com.delivery.tracking.grpc.ShipmentRoutePoint;
import com.delivery.tracking.grpc.ShipmentRouteSummary;
import com.delivery.tracking.grpc.ShipmentTrackingServiceGrpc;
import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * gRPC-клієнт dispatch-service -> tracking-service для Unary та Client Streaming.
 * <p>
 * Повтори (retries) при {@code UNAVAILABLE} виконує сам gRPC-канал за service config
 * з application.properties; цей клас бачить лише фінальний результат виклику.
 * Якщо всі спроби вичерпано, {@link StatusRuntimeException} перетворюється на
 * {@link TrackingGrpcException}, а HTTP-шар відповідає 503 Service Unavailable.
 */
@Component
public class TrackingGrpcClient {

    private static final Logger log = LoggerFactory.getLogger(TrackingGrpcClient.class);

    // Страховка на випадок, якщо deadline не задано в service config.
    private static final Duration STREAM_RESULT_TIMEOUT = Duration.ofSeconds(60);

    private final ShipmentTrackingServiceGrpc.ShipmentTrackingServiceBlockingStub blockingStub;
    private final ShipmentTrackingServiceGrpc.ShipmentTrackingServiceStub asyncStub;

    public TrackingGrpcClient(
            ShipmentTrackingServiceGrpc.ShipmentTrackingServiceBlockingStub blockingStub,
            ShipmentTrackingServiceGrpc.ShipmentTrackingServiceStub asyncStub
    ) {
        this.blockingStub = blockingStub;
        this.asyncStub = asyncStub;
    }

    // Unary
    public LatestLocationResponse getLatestLocation(long shipmentId) {
        GetLatestShipmentLocationRequest request = GetLatestShipmentLocationRequest.newBuilder()
                .setShipmentId(shipmentId)
                .build();

        try {
            ShipmentLocationUpdate update = blockingStub.getLatestShipmentLocation(request);
            log.info("gRPC latest location received: shipmentId={}, locationId={}", shipmentId, update.getLocationId());
            return toResponse(update);
        } catch (StatusRuntimeException ex) {
            throw translate("GetLatestShipmentLocation", shipmentId, ex);
        }
    }

    // Client streaming
    public RouteUploadResponse uploadRoute(long shipmentId, String uploadId, List<RoutePointRequest> points) {
        CompletableFuture<ShipmentRouteSummary> result = new CompletableFuture<>();

        StreamObserver<ShipmentRoutePoint> requestObserver = asyncStub.recordShipmentRoute(new StreamObserver<>() {
            @Override
            public void onNext(ShipmentRouteSummary summary) {
                result.complete(summary);
            }

            @Override
            public void onError(Throwable error) {
                result.completeExceptionally(error);
            }

            @Override
            public void onCompleted() {
                if (!result.isDone()) {
                    result.completeExceptionally(Status.INTERNAL
                            .withDescription("server completed without summary")
                            .asRuntimeException());
                }
            }
        });

        log.info("gRPC route upload started: uploadId={}, shipmentId={}, points={}", uploadId, shipmentId, points.size());

        try {
            for (RoutePointRequest point : points) {
                if (result.isDone()) {
                    // Сервер уже відхилив потік (наприклад, INVALID_ARGUMENT) — далі не надсилаємо.
                    break;
                }
                requestObserver.onNext(toProto(shipmentId, uploadId, point));
            }
            requestObserver.onCompleted();
        } catch (RuntimeException ex) {
            requestObserver.onError(ex);
            throw ex;
        }

        ShipmentRouteSummary summary = await(result, shipmentId);

        log.info("gRPC route upload finished: uploadId={}, shipmentId={}, accepted={}, duplicate={}",
                uploadId, shipmentId, summary.getAcceptedCount(), summary.getDuplicate());

        return new RouteUploadResponse(
                summary.getUploadId(),
                summary.getShipmentId(),
                summary.getAcceptedCount(),
                summary.getFirstLocationId(),
                summary.getLastLocationId(),
                summary.getDuplicate()
        );
    }

    private ShipmentRouteSummary await(CompletableFuture<ShipmentRouteSummary> result, long shipmentId) {
        try {
            return result.get(STREAM_RESULT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException ex) {
            throw translate("RecordShipmentRoute", shipmentId, ex.getCause());
        } catch (TimeoutException ex) {
            throw translate("RecordShipmentRoute", shipmentId,
                    Status.DEADLINE_EXCEEDED.withDescription("no summary received").asRuntimeException());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw translate("RecordShipmentRoute", shipmentId, Status.CANCELLED.withCause(ex).asRuntimeException());
        }
    }

    private TrackingGrpcException translate(String method, long shipmentId, Throwable error) {
        Status status = Status.fromThrowable(error);

        if (status.getCode() == Status.Code.UNAVAILABLE) {
            log.error("gRPC {} failed: tracking-service unavailable after all retries: shipmentId={}, description={}",
                    method, shipmentId, status.getDescription());
        } else {
            log.warn("gRPC {} failed: shipmentId={}, status={}, description={}",
                    method, shipmentId, status.getCode(), status.getDescription());
        }

        return new TrackingGrpcException(status, error);
    }

    private static LatestLocationResponse toResponse(ShipmentLocationUpdate update) {
        return new LatestLocationResponse(
                update.getLocationId(),
                update.getShipmentId(),
                update.getLatitude(),
                update.getLongitude(),
                Instant.ofEpochSecond(update.getRecordedAt().getSeconds(), update.getRecordedAt().getNanos())
        );
    }

    private static ShipmentRoutePoint toProto(long shipmentId, String uploadId, RoutePointRequest point) {
        Instant recordedAt = point.recordedAt();

        return ShipmentRoutePoint.newBuilder()
                .setUploadId(uploadId)
                .setShipmentId(shipmentId)
                .setLatitude(point.latitude())
                .setLongitude(point.longitude())
                .setRecordedAt(Timestamp.newBuilder()
                        .setSeconds(recordedAt.getEpochSecond())
                        .setNanos(recordedAt.getNano())
                        .build())
                .build();
    }
}
