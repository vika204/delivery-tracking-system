package com.delivery.dispatch.grpc;

import com.delivery.dispatch.config.TrackingGrpcProperties;
import com.delivery.dispatch.dto.LiveLocationDecisionResponse;
import com.delivery.dispatch.dto.LocationPointRequest;
import com.delivery.dispatch.dto.RouteUploadResponse;
import com.delivery.dispatch.exception.TrackingGrpcException;
import com.delivery.tracking.grpc.CourierLocationDecision;
import com.delivery.tracking.grpc.CourierLocationReport;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Component
public class TrackingGrpcClient {

    private static final Logger log = LoggerFactory.getLogger(TrackingGrpcClient.class);

    private final ShipmentTrackingServiceGrpc.ShipmentTrackingServiceStub stub;
    private final TrackingGrpcProperties properties;

    public TrackingGrpcClient(ShipmentTrackingServiceGrpc.ShipmentTrackingServiceStub stub,
                              TrackingGrpcProperties properties) {
        this.stub = stub;
        this.properties = properties;
    }

    public RouteUploadResponse uploadRoute(long shipmentId, String uploadId, List<LocationPointRequest> points) {
        CompletableFuture<ShipmentRouteSummary> summary = new CompletableFuture<>();
        StreamObserver<ShipmentRoutePoint> requests = deadlineStub().recordShipmentRoute(new StreamObserver<>() {
            @Override
            public void onNext(ShipmentRouteSummary value) {
                summary.complete(value);
            }

            @Override
            public void onError(Throwable error) {
                summary.completeExceptionally(error);
            }

            @Override
            public void onCompleted() {
            }
        });

        for (LocationPointRequest point : points) {
            ShipmentRoutePoint.Builder message = ShipmentRoutePoint.newBuilder()
                    .setUploadId(uploadId)
                    .setShipmentId(shipmentId)
                    .setLatitude(coordinate(point.latitude()))
                    .setLongitude(coordinate(point.longitude()));
            if (point.recordedAt() != null) {
                message.setRecordedAt(timestamp(point.recordedAt()));
            }
            requests.onNext(message.build());
        }
        requests.onCompleted();

        try {
            return toResponse(await(summary));
        } catch (StatusRuntimeException ex) {
            throw new TrackingGrpcException(ex);
        }
    }

    /**
     * Канал повторює потік лише до першої відповіді сервера. Якщо потік обірвався пізніше,
     * клієнт перепідключається з тим самим backoff і досилає лише непідтверджені точки.
     */
    public List<LiveLocationDecisionResponse> streamLiveLocations(
            long shipmentId, String sessionId, List<LocationPointRequest> points) {
        List<CourierLocationReport> reports = toReports(shipmentId, sessionId, points);
        Map<String, CourierLocationDecision> decisions = new LinkedHashMap<>();
        TrackingGrpcProperties.Retry retry = properties.retry();

        for (int attempt = 1; ; attempt++) {
            int confirmedBefore = decisions.size();
            List<CourierLocationReport> pending = reports.stream()
                    .filter(report -> !decisions.containsKey(report.getPointId()))
                    .toList();
            try {
                streamOnce(pending, decisions);
                break;
            } catch (StatusRuntimeException ex) {
                boolean progressed = decisions.size() > confirmedBefore;
                if (ex.getStatus().getCode() != Status.Code.UNAVAILABLE || !progressed || attempt >= retry.maxAttempts()) {
                    throw new TrackingGrpcException(ex);
                }
                Duration backoff = retry.backoff(attempt);
                log.warn("gRPC stream interrupted, reconnecting: method=StreamCourierLocations, sessionId={}, attempt={}, remainingPoints={}, backoffMs={}",
                        sessionId, attempt + 1, reports.size() - decisions.size(), backoff.toMillis());
                sleep(backoff);
            }
        }

        return decisions.values().stream().map(TrackingGrpcClient::toResponse).toList();
    }

    private void streamOnce(List<CourierLocationReport> reports, Map<String, CourierLocationDecision> decisions) {
        CompletableFuture<Void> completed = new CompletableFuture<>();
        StreamObserver<CourierLocationReport> requests = deadlineStub().streamCourierLocations(new StreamObserver<>() {
            @Override
            public void onNext(CourierLocationDecision decision) {
                decisions.put(decision.getPointId(), decision);
            }

            @Override
            public void onError(Throwable error) {
                completed.completeExceptionally(error);
            }

            @Override
            public void onCompleted() {
                completed.complete(null);
            }
        });

        reports.forEach(requests::onNext);
        requests.onCompleted();
        await(completed);
    }

    private ShipmentTrackingServiceGrpc.ShipmentTrackingServiceStub deadlineStub() {
        return stub.withDeadlineAfter(properties.deadline().toMillis(), TimeUnit.MILLISECONDS);
    }

    private static <T> T await(CompletableFuture<T> future) {
        try {
            return future.get();
        } catch (ExecutionException ex) {
            throw Status.fromThrowable(ex.getCause()).asRuntimeException();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw Status.CANCELLED.withCause(ex).asRuntimeException();
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new TrackingGrpcException(Status.CANCELLED.withCause(ex).asRuntimeException());
        }
    }

    private static List<CourierLocationReport> toReports(long shipmentId, String sessionId, List<LocationPointRequest> points) {
        List<CourierLocationReport> reports = new ArrayList<>(points.size());
        for (int i = 0; i < points.size(); i++) {
            LocationPointRequest point = points.get(i);
            String pointId = point.pointId() == null || point.pointId().isBlank()
                    ? sessionId + "-" + (i + 1)
                    : point.pointId().trim();
            CourierLocationReport.Builder report = CourierLocationReport.newBuilder()
                    .setPointId(pointId)
                    .setShipmentId(shipmentId)
                    .setLatitude(coordinate(point.latitude()))
                    .setLongitude(coordinate(point.longitude()));
            if (point.recordedAt() != null) {
                report.setRecordedAt(timestamp(point.recordedAt()));
            }
            reports.add(report.build());
        }
        return reports;
    }

    // Відсутні значення надсилаються навмисно: tracking-service відхиляє такі точки поштучно.
    private static double coordinate(Double value) {
        return value == null ? Double.NaN : value;
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();
    }

    private static RouteUploadResponse toResponse(ShipmentRouteSummary summary) {
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

    private static LiveLocationDecisionResponse toResponse(CourierLocationDecision decision) {
        LiveLocationDecisionResponse.Status status = switch (decision.getStatus()) {
            case COURIER_LOCATION_STATUS_ACCEPTED -> LiveLocationDecisionResponse.Status.ACCEPTED;
            case COURIER_LOCATION_STATUS_SUSPICIOUS_JUMP -> LiveLocationDecisionResponse.Status.SUSPICIOUS_JUMP;
            default -> LiveLocationDecisionResponse.Status.REJECTED;
        };
        return new LiveLocationDecisionResponse(
                decision.getPointId(),
                decision.getShipmentId(),
                status,
                decision.getSaved(),
                decision.getLocationId() == 0 ? null : decision.getLocationId(),
                decision.getReason(),
                decision.getDistanceMeters(),
                decision.getSpeedKmh(),
                decision.getDuplicate()
        );
    }
}
