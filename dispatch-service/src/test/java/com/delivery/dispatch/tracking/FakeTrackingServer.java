package com.delivery.dispatch.tracking;

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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Тестовий gRPC-сервер tracking-service на реальному TCP-порту.
 * Дозволяє імітувати тимчасові збої (перші N викликів -> обраний статус)
 * та повну недоступність (stop/start на тому ж порту).
 */
class FakeTrackingServer extends ShipmentTrackingServiceGrpc.ShipmentTrackingServiceImplBase {

    final AtomicInteger routeCalls = new AtomicInteger();
    final AtomicInteger liveStreamCalls = new AtomicInteger();
    final AtomicInteger failuresRemaining = new AtomicInteger();
    final List<List<ShipmentRoutePoint>> receivedRoutes = new CopyOnWriteArrayList<>();
    final List<List<CourierLocationReport>> receivedLiveStreams = new CopyOnWriteArrayList<>();
    final Set<String> savedLivePointIds = ConcurrentHashMap.newKeySet();

    volatile Status failureStatus = Status.UNAVAILABLE.withDescription("simulated outage");
    volatile int failMidStreamAfterPoints = -1;

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
        failMidStreamAfterPoints = -1;
        receivedRoutes.clear();
        receivedLiveStreams.clear();
        savedLivePointIds.clear();
        failureStatus = Status.UNAVAILABLE.withDescription("simulated outage");
    }

    void failNextCalls(int count, Status status) {
        failureStatus = status;
        failMidStreamAfterPoints = -1;
        failuresRemaining.set(count);
    }

    void failMidStreamNextCalls(int count, int afterPoints, Status status) {
        failureStatus = status;
        failMidStreamAfterPoints = afterPoints;
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

                // Збій після отримання всіх точок, але до відповіді — найгірший випадок для retry.
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

                int midStreamCut = failMidStreamAfterPoints;
                if (midStreamCut < 0 && shouldFail()) {
                    // Збій до першої відповіді — перехоплюється та повторюється gRPC service-config.
                    responseObserver.onError(failureStatus.asRuntimeException());
                    return;
                }

                boolean failThisMidStream = midStreamCut >= 0 && shouldFail();
                int limit = failThisMidStream ? Math.min(midStreamCut, reports.size()) : reports.size();

                for (int i = 0; i < limit; i++) {
                    responseObserver.onNext(evaluateReport(reports.get(i), i + 1));
                }

                if (failThisMidStream) {
                    responseObserver.onError(failureStatus.asRuntimeException());
                    return;
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
                    .setPointId(report.getPointId())
                    .setShipmentId(report.getShipmentId())
                    .setStatus(CourierLocationStatus.COURIER_LOCATION_STATUS_REJECTED)
                    .setSaved(false)
                    .setReason("invalid coordinates or missing recordedAt")
                    .build();
        }

        if (Math.abs(report.getLatitude() - 50.45) > 1.0) {
            return CourierLocationDecision.newBuilder()
                    .setPointId(report.getPointId())
                    .setShipmentId(report.getShipmentId())
                    .setStatus(CourierLocationStatus.COURIER_LOCATION_STATUS_SUSPICIOUS_JUMP)
                    .setSaved(false)
                    .setDistanceMeters(450_000.0)
                    .setSpeedKmh(162_000.0)
                    .setReason("suspicious distance/speed jump")
                    .build();
        }

        boolean duplicate = !savedLivePointIds.add(report.getPointId());
        return CourierLocationDecision.newBuilder()
                .setPointId(report.getPointId())
                .setShipmentId(report.getShipmentId())
                .setStatus(CourierLocationStatus.COURIER_LOCATION_STATUS_ACCEPTED)
                .setSaved(true)
                .setLocationId(seq)
                .setReason(duplicate ? "Duplicate point ignored" : "Coordinate accepted")
                .setDistanceMeters(120.0)
                .setSpeedKmh(28.5)
                .setDuplicate(duplicate)
                .build();
    }

    // Спрощена копія серверної перевірки tracking-service.
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
