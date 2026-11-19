package com.delivery.dispatch.grpc;

import com.delivery.dispatch.dto.LiveLocationDecisionResponse;
import com.delivery.dispatch.dto.LocationPointRequest;
import com.delivery.dispatch.dto.RouteUploadResponse;
import com.delivery.tracking.api.ProtoTimestamps;
import com.delivery.tracking.grpc.CourierLocationDecision;
import com.delivery.tracking.grpc.CourierLocationReport;
import com.delivery.tracking.grpc.ShipmentRoutePoint;
import com.delivery.tracking.grpc.ShipmentRouteSummary;
import com.delivery.tracking.grpc.ShipmentTrackingServiceGrpc;
import com.delivery.tracking.grpcclient.TrackingGrpcException;
import com.delivery.tracking.grpcclient.TrackingGrpcProperties;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Component
public class TrackingGrpcClient {

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
                message.setRecordedAt(ProtoTimestamps.toTimestamp(point.recordedAt()));
            }
            requests.onNext(message.build());
        }
        requests.onCompleted();

        return toResponse(await(summary));
    }

    public List<LiveLocationDecisionResponse> streamLiveLocations(long shipmentId, List<LocationPointRequest> points) {
        List<LiveLocationDecisionResponse> decisions = new ArrayList<>();
        CompletableFuture<Void> completed = new CompletableFuture<>();

        StreamObserver<CourierLocationReport> requests = deadlineStub().streamCourierLocations(new StreamObserver<>() {
            @Override
            public void onNext(CourierLocationDecision decision) {
                decisions.add(toResponse(decision));
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

        for (LocationPointRequest point : points) {
            CourierLocationReport.Builder report = CourierLocationReport.newBuilder()
                    .setShipmentId(shipmentId)
                    .setLatitude(coordinate(point.latitude()))
                    .setLongitude(coordinate(point.longitude()));
            if (point.recordedAt() != null) {
                report.setRecordedAt(ProtoTimestamps.toTimestamp(point.recordedAt()));
            }
            requests.onNext(report.build());
        }
        requests.onCompleted();

        await(completed);
        return List.copyOf(decisions);
    }

    private ShipmentTrackingServiceGrpc.ShipmentTrackingServiceStub deadlineStub() {
        return stub.withDeadlineAfter(properties.deadline().toMillis(), TimeUnit.MILLISECONDS);
    }

    private static <T> T await(CompletableFuture<T> future) {
        try {
            return future.get();
        } catch (ExecutionException ex) {
            throw new TrackingGrpcException(ex.getCause());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new TrackingGrpcException(Status.CANCELLED.withCause(ex).asRuntimeException());
        }
    }

    private static double coordinate(Double value) {
        return value == null ? Double.NaN : value;
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
                decision.getShipmentId(),
                status,
                decision.getSaved(),
                decision.getLocationId() == 0 ? null : decision.getLocationId(),
                decision.getReason(),
                decision.getDistanceMeters()
        );
    }
}
