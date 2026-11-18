package com.delivery.dispatch.tracking;

import com.delivery.dispatch.config.TrackingGrpcProperties;
import com.delivery.dispatch.tracking.dto.LiveLocationDecisionDto;
import com.delivery.dispatch.tracking.dto.LiveLocationPointRequest;
import com.delivery.dispatch.tracking.dto.LiveLocationStreamResponse;
import com.delivery.tracking.grpc.CourierLocationDecision;
import com.delivery.tracking.grpc.CourierLocationReport;
import com.delivery.tracking.grpc.CourierLocationStatus;
import com.delivery.tracking.grpc.ShipmentTrackingServiceGrpc;
import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class CourierLiveTrackingGrpcClient {

    private static final Logger log = LoggerFactory.getLogger(CourierLiveTrackingGrpcClient.class);
    private static final Duration STREAM_RESULT_TIMEOUT = Duration.ofSeconds(60);

    private final ShipmentTrackingServiceGrpc.ShipmentTrackingServiceStub asyncStub;
    private final TrackingGrpcProperties.Retry retry;

    public CourierLiveTrackingGrpcClient(
            ShipmentTrackingServiceGrpc.ShipmentTrackingServiceStub asyncStub,
            TrackingGrpcProperties properties
    ) {
        this.asyncStub = asyncStub;
        this.retry = properties.retry();
    }

    public LiveLocationStreamResponse streamLocations(
            long shipmentId,
            String sessionId,
            List<LiveLocationPointRequest> points
    ) {
        List<CourierLocationReport> allReports = buildReports(shipmentId, sessionId, points);
        Map<String, CourierLocationDecision> decisionsByPointId = new LinkedHashMap<>();

        log.info("gRPC bidirectional location stream started: sessionId={}, shipmentId={}, points={}",
                sessionId, shipmentId, allReports.size());

        int reconnectAttempt = 0;
        while (true) {
            List<CourierLocationReport> pendingReports = allReports.stream()
                    .filter(report -> !decisionsByPointId.containsKey(report.getPointId()))
                    .toList();

            if (pendingReports.isEmpty()) {
                break;
            }

            List<CourierLocationDecision> attemptDecisions = new CopyOnWriteArrayList<>();
            CompletableFuture<Void> completion = new CompletableFuture<>();

            StreamObserver<CourierLocationReport> requestObserver = asyncStub.streamCourierLocations(
                    new StreamObserver<>() {
                        @Override
                        public void onNext(CourierLocationDecision decision) {
                            attemptDecisions.add(decision);
                            log.info("gRPC live location decision received: sessionId={}, pointId={}, shipmentId={}, status={}, saved={}, locationId={}, duplicate={}",
                                    sessionId, decision.getPointId(), decision.getShipmentId(),
                                    decision.getStatus(), decision.getSaved(), decision.getLocationId(), decision.getDuplicate());
                        }

                        @Override
                        public void onError(Throwable error) {
                            completion.completeExceptionally(error);
                        }

                        @Override
                        public void onCompleted() {
                            completion.complete(null);
                        }
                    }
            );

            try {
                for (CourierLocationReport report : pendingReports) {
                    if (completion.isDone()) {
                        break;
                    }
                    requestObserver.onNext(report);
                }
                requestObserver.onCompleted();
            } catch (RuntimeException ex) {
                requestObserver.onError(ex);
                throw ex;
            }

            Throwable failure = awaitCompletion(completion);
            for (CourierLocationDecision decision : attemptDecisions) {
                decisionsByPointId.put(decision.getPointId(), decision);
            }

            if (failure == null) {
                break;
            }

            Status status = Status.fromThrowable(failure);
            boolean midStreamFailure = !attemptDecisions.isEmpty();

            if (status.getCode() == Status.Code.UNAVAILABLE
                    && midStreamFailure
                    && reconnectAttempt < retry.maxAttempts() - 1) {
                reconnectAttempt++;
                long delayMs = computeBackoffMs(reconnectAttempt);
                int remaining = allReports.size() - decisionsByPointId.size();
                log.warn("gRPC StreamCourierLocations interrupted mid-stream with UNAVAILABLE; reconnecting: sessionId={}, shipmentId={}, reconnectAttempt={}/{}, remainingPoints={}, backoffMs={}",
                        sessionId, shipmentId, reconnectAttempt, retry.maxAttempts() - 1, remaining, delayMs);
                sleepBackoff(shipmentId, delayMs);
                continue;
            }

            throw translate(shipmentId, failure);
        }

        List<LiveLocationDecisionDto> decisionDtos = new ArrayList<>(decisionsByPointId.size());
        int acceptedCount = 0;
        int rejectedCount = 0;
        int suspiciousCount = 0;

        for (CourierLocationDecision decision : decisionsByPointId.values()) {
            if (decision.getStatus() == CourierLocationStatus.COURIER_LOCATION_STATUS_ACCEPTED) {
                acceptedCount++;
            } else if (decision.getStatus() == CourierLocationStatus.COURIER_LOCATION_STATUS_SUSPICIOUS_JUMP) {
                suspiciousCount++;
            } else {
                rejectedCount++;
            }
            decisionDtos.add(toDto(decision));
        }

        log.info("gRPC bidirectional location stream finished: sessionId={}, shipmentId={}, sent={}, accepted={}, rejected={}, suspiciousJump={}",
                sessionId, shipmentId, allReports.size(), acceptedCount, rejectedCount, suspiciousCount);

        return new LiveLocationStreamResponse(
                sessionId,
                shipmentId,
                allReports.size(),
                acceptedCount,
                rejectedCount,
                suspiciousCount,
                decisionDtos
        );
    }

    private Throwable awaitCompletion(CompletableFuture<Void> completion) {
        try {
            completion.get(STREAM_RESULT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            return null;
        } catch (ExecutionException ex) {
            return ex.getCause();
        } catch (TimeoutException ex) {
            return Status.DEADLINE_EXCEEDED.withDescription("live location stream timed out").asRuntimeException();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Status.CANCELLED.withCause(ex).asRuntimeException();
        }
    }

    private long computeBackoffMs(int reconnectAttempt) {
        return retry.backoff(reconnectAttempt).toMillis();
    }

    private void sleepBackoff(long shipmentId, long delayMs) {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw translate(shipmentId, Status.CANCELLED.withCause(ex).asRuntimeException());
        }
    }

    private TrackingGrpcException translate(long shipmentId, Throwable error) {
        Status status = Status.fromThrowable(error);

        if (status.getCode() == Status.Code.UNAVAILABLE) {
            log.error("gRPC StreamCourierLocations failed: tracking-service unavailable after all retries: shipmentId={}, description={}",
                    shipmentId, status.getDescription());
        } else {
            log.warn("gRPC StreamCourierLocations failed: shipmentId={}, status={}, description={}",
                    shipmentId, status.getCode(), status.getDescription());
        }

        return new TrackingGrpcException(status, error);
    }

    private static List<CourierLocationReport> buildReports(
            long shipmentId,
            String sessionId,
            List<LiveLocationPointRequest> points
    ) {
        List<CourierLocationReport> reports = new ArrayList<>(points.size());
        for (int i = 0; i < points.size(); i++) {
            LiveLocationPointRequest point = points.get(i);
            String pointId = (point != null && point.pointId() != null && !point.pointId().isBlank())
                    ? point.pointId().trim()
                    : sessionId + "-" + (i + 1);

            CourierLocationReport.Builder builder = CourierLocationReport.newBuilder()
                    .setPointId(pointId)
                    .setShipmentId(shipmentId)
                    .setLatitude(point == null || point.latitude() == null ? Double.NaN : point.latitude())
                    .setLongitude(point == null || point.longitude() == null ? Double.NaN : point.longitude());

            if (point != null && point.recordedAt() != null) {
                builder.setRecordedAt(Timestamp.newBuilder()
                        .setSeconds(point.recordedAt().getEpochSecond())
                        .setNanos(point.recordedAt().getNano())
                        .build());
            }

            reports.add(builder.build());
        }
        return reports;
    }

    private static LiveLocationDecisionDto toDto(CourierLocationDecision decision) {
        String status = switch (decision.getStatus()) {
            case COURIER_LOCATION_STATUS_ACCEPTED -> "ACCEPTED";
            case COURIER_LOCATION_STATUS_REJECTED -> "REJECTED";
            case COURIER_LOCATION_STATUS_SUSPICIOUS_JUMP -> "SUSPICIOUS_JUMP";
            case COURIER_LOCATION_STATUS_UNSPECIFIED, UNRECOGNIZED -> "UNSPECIFIED";
        };

        return new LiveLocationDecisionDto(
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
