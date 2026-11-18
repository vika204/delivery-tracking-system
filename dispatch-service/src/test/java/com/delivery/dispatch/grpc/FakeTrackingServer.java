package com.delivery.dispatch.grpc;

import com.delivery.tracking.grpc.CourierLocationDecision;
import com.delivery.tracking.grpc.CourierLocationReport;
import com.delivery.tracking.grpc.CourierLocationStatus;
import com.delivery.tracking.grpc.ShipmentRoutePoint;
import com.delivery.tracking.grpc.ShipmentRouteSummary;
import com.delivery.tracking.grpc.ShipmentTrackingServiceGrpc;
import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class FakeTrackingServer extends ShipmentTrackingServiceGrpc.ShipmentTrackingServiceImplBase {

    final AtomicInteger routeCalls = new AtomicInteger();
    final AtomicInteger liveStreamCalls = new AtomicInteger();
    final AtomicInteger failuresRemaining = new AtomicInteger();
    final List<List<ShipmentRoutePoint>> receivedRoutes = new CopyOnWriteArrayList<>();
    final List<List<CourierLocationReport>> receivedLiveStreams = new CopyOnWriteArrayList<>();

    volatile Status failureStatus = Status.UNAVAILABLE.withDescription("simulated outage");

    private final int port;
    private Server server;

    FakeTrackingServer() {
        this.port = freePort();
    }

    int port() {
        return port;
    }

    synchronized void start() throws IOException {
        if (server != null) {
            return;
        }
        server = Grpc.newServerBuilderForPort(port, InsecureServerCredentials.create())
                .addService(this)
                .build()
                .start();
    }

    synchronized void stop() throws InterruptedException {
        if (server != null) {
            server.shutdownNow();
            server.awaitTermination(5, TimeUnit.SECONDS);
            server = null;
        }
    }

    void reset() {
        routeCalls.set(0);
        liveStreamCalls.set(0);
        failuresRemaining.set(0);
        receivedRoutes.clear();
        receivedLiveStreams.clear();
        failureStatus = Status.UNAVAILABLE.withDescription("simulated outage");
    }

    void failNextCalls(int count, Status status) {
        failureStatus = status;
        failuresRemaining.set(count);
    }

    @Override
    public StreamObserver<ShipmentRoutePoint> recordShipmentRoute(StreamObserver<ShipmentRouteSummary> responseObserver) {
        routeCalls.incrementAndGet();
        List<ShipmentRoutePoint> points = new ArrayList<>();

        return new StreamObserver<>() {
            @Override
            public void onNext(ShipmentRoutePoint point) {
                points.add(point);
            }

            @Override
            public void onError(Throwable error) {
            }

            @Override
            public void onCompleted() {
                receivedRoutes.add(List.copyOf(points));

                if (shouldFail()) {
                    responseObserver.onError(failureStatus.asRuntimeException());
                    return;
                }

                ShipmentRoutePoint first = points.getFirst();
                int saved = (int) points.stream().filter(FakeTrackingServer::isValid).count();
                responseObserver.onNext(ShipmentRouteSummary.newBuilder()
                        .setUploadId(first.getUploadId())
                        .setShipmentId(first.getShipmentId())
                        .setReceivedCount(points.size())
                        .setSavedCount(saved)
                        .setRejectedCount(points.size() - saved)
                        .setFirstLocationId(saved == 0 ? 0 : 1)
                        .setLastLocationId(saved)
                        .build());
                responseObserver.onCompleted();
            }
        };
    }

    @Override
    public StreamObserver<CourierLocationReport> streamCourierLocations(StreamObserver<CourierLocationDecision> responseObserver) {
        liveStreamCalls.incrementAndGet();
        List<CourierLocationReport> reports = new ArrayList<>();

        return new StreamObserver<>() {
            @Override
            public void onNext(CourierLocationReport report) {
                reports.add(report);
            }

            @Override
            public void onError(Throwable error) {
            }

            @Override
            public void onCompleted() {
                receivedLiveStreams.add(List.copyOf(reports));

                if (shouldFail()) {
                    responseObserver.onError(failureStatus.asRuntimeException());
                    return;
                }

                for (int i = 0; i < reports.size(); i++) {
                    responseObserver.onNext(evaluateReport(reports.get(i), i + 1));
                }
                responseObserver.onCompleted();
            }
        };
    }

    private CourierLocationDecision evaluateReport(CourierLocationReport report, long seq) {
        if (!(report.getLatitude() >= -90 && report.getLatitude() <= 90)
                || !(report.getLongitude() >= -180 && report.getLongitude() <= 180)
                || !report.hasRecordedAt()) {
            return CourierLocationDecision.newBuilder()
                    .setShipmentId(report.getShipmentId())
                    .setStatus(CourierLocationStatus.COURIER_LOCATION_STATUS_REJECTED)
                    .setSaved(false)
                    .setReason("invalid coordinates or missing recordedAt")
                    .build();
        }

        if (Math.abs(report.getLatitude() - 50.45) > 1.0) {
            return CourierLocationDecision.newBuilder()
                    .setShipmentId(report.getShipmentId())
                    .setStatus(CourierLocationStatus.COURIER_LOCATION_STATUS_SUSPICIOUS_JUMP)
                    .setSaved(false)
                    .setDistanceMeters(450_000.0)
                    .setReason("suspicious location jump")
                    .build();
        }

        return CourierLocationDecision.newBuilder()
                .setShipmentId(report.getShipmentId())
                .setStatus(CourierLocationStatus.COURIER_LOCATION_STATUS_ACCEPTED)
                .setSaved(true)
                .setLocationId(seq)
                .setReason("Coordinate accepted")
                .setDistanceMeters(120.0)
                .build();
    }

    private static boolean isValid(ShipmentRoutePoint point) {
        return point.getLatitude() >= -90 && point.getLatitude() <= 90
                && point.getLongitude() >= -180 && point.getLongitude() <= 180
                && point.hasRecordedAt();
    }

    private boolean shouldFail() {
        return failuresRemaining.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0;
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
