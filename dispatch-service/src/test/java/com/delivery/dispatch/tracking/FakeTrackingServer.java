package com.delivery.dispatch.tracking;

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

/**
 * Тестовий gRPC-сервер tracking-service на реальному TCP-порту.
 * Дозволяє імітувати тимчасові збої (перші N викликів -> обраний статус)
 * та повну недоступність (stop/start на тому ж порту).
 */
class FakeTrackingServer extends ShipmentTrackingServiceGrpc.ShipmentTrackingServiceImplBase {

    final AtomicInteger routeCalls = new AtomicInteger();
    final AtomicInteger failuresRemaining = new AtomicInteger();
    final List<List<ShipmentRoutePoint>> receivedRoutes = new CopyOnWriteArrayList<>();

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
        failuresRemaining.set(0);
        receivedRoutes.clear();
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
