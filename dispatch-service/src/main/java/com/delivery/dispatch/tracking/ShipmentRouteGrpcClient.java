package com.delivery.dispatch.tracking;

import com.delivery.dispatch.tracking.dto.RoutePointRequest;
import com.delivery.dispatch.tracking.dto.RouteUploadResponse;
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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * gRPC Client Streaming-клієнт dispatch-service -> tracking-service.
 * Імітує застосунок кур'єра, що після відновлення зв'язку відправляє накопичені координати.
 * <p>
 * Повтори (retries) при {@code UNAVAILABLE} виконує сам gRPC-канал за service config
 * з application.properties: він буферизує надіслані повідомлення й відтворює весь потік.
 * Якщо всі спроби вичерпано, {@link StatusRuntimeException} перетворюється на
 * {@link TrackingGrpcException}, а HTTP-шар відповідає 503 Service Unavailable.
 */
@Component
public class ShipmentRouteGrpcClient {

    private static final Logger log = LoggerFactory.getLogger(ShipmentRouteGrpcClient.class);

    // Страховка на випадок, якщо deadline не задано в service config.
    private static final Duration STREAM_RESULT_TIMEOUT = Duration.ofSeconds(60);

    private final ShipmentTrackingServiceGrpc.ShipmentTrackingServiceStub asyncStub;

    public ShipmentRouteGrpcClient(ShipmentTrackingServiceGrpc.ShipmentTrackingServiceStub asyncStub) {
        this.asyncStub = asyncStub;
    }

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

        log.info("gRPC route upload finished: uploadId={}, shipmentId={}, received={}, saved={}, rejected={}, duplicate={}",
                uploadId, shipmentId, summary.getReceivedCount(), summary.getSavedCount(),
                summary.getRejectedCount(), summary.getDuplicate());

        return new RouteUploadResponse(
                summary.getUploadId(),
                summary.getShipmentId(),
                summary.getReceivedCount(),
                summary.getSavedCount(),
                summary.getRejectedCount(),
                summary.getFirstLocationId() == 0 ? null : summary.getFirstLocationId(),
                summary.getLastLocationId() == 0 ? null : summary.getLastLocationId(),
                summary.getDuplicate()
        );
    }

    private ShipmentRouteSummary await(CompletableFuture<ShipmentRouteSummary> result, long shipmentId) {
        try {
            return result.get(STREAM_RESULT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException ex) {
            throw translate(shipmentId, ex.getCause());
        } catch (TimeoutException ex) {
            throw translate(shipmentId, Status.DEADLINE_EXCEEDED.withDescription("no summary received").asRuntimeException());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw translate(shipmentId, Status.CANCELLED.withCause(ex).asRuntimeException());
        }
    }

    private TrackingGrpcException translate(long shipmentId, Throwable error) {
        Status status = Status.fromThrowable(error);

        if (status.getCode() == Status.Code.UNAVAILABLE) {
            log.error("gRPC RecordShipmentRoute failed: tracking-service unavailable after all retries: shipmentId={}, description={}",
                    shipmentId, status.getDescription());
        } else {
            log.warn("gRPC RecordShipmentRoute failed: shipmentId={}, status={}, description={}",
                    shipmentId, status.getCode(), status.getDescription());
        }

        return new TrackingGrpcException(status, error);
    }

    /**
     * Координати передаються як є: перевіряє їх tracking-service і рахує відхилені.
     * Відсутні значення кодуються так, щоб сервер їх відхилив: NaN для координат,
     * незаповнений recorded_at для часу.
     */
    private static ShipmentRoutePoint toProto(long shipmentId, String uploadId, RoutePointRequest point) {
        ShipmentRoutePoint.Builder builder = ShipmentRoutePoint.newBuilder()
                .setUploadId(uploadId)
                .setShipmentId(shipmentId)
                .setLatitude(point == null || point.latitude() == null ? Double.NaN : point.latitude())
                .setLongitude(point == null || point.longitude() == null ? Double.NaN : point.longitude());

        if (point != null && point.recordedAt() != null) {
            builder.setRecordedAt(Timestamp.newBuilder()
                    .setSeconds(point.recordedAt().getEpochSecond())
                    .setNanos(point.recordedAt().getNano())
                    .build());
        }

        return builder.build();
    }
}
