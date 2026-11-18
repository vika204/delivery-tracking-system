package com.delivery.notification.grpc;

import com.delivery.tracking.grpc.GetShipmentDeliveryInfoRequest;
import com.delivery.tracking.grpc.ShipmentDeliveryInfo;
import com.delivery.tracking.grpc.ShipmentTrackingServiceGrpc;
import com.google.protobuf.Timestamp;
import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class FakeTrackingServer extends ShipmentTrackingServiceGrpc.ShipmentTrackingServiceImplBase {

    final AtomicInteger unaryCalls = new AtomicInteger();
    final AtomicInteger failuresRemaining = new AtomicInteger();

    volatile Status failureStatus = Status.UNAVAILABLE.withDescription("simulated outage");
    volatile boolean hasLocation = true;

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
        unaryCalls.set(0);
        failuresRemaining.set(0);
        failureStatus = Status.UNAVAILABLE.withDescription("simulated outage");
        hasLocation = true;
    }

    void failNextCalls(int count, Status status) {
        failureStatus = status;
        failuresRemaining.set(count);
    }

    @Override
    public void getShipmentDeliveryInfo(GetShipmentDeliveryInfoRequest request,
                                        StreamObserver<ShipmentDeliveryInfo> responseObserver) {
        unaryCalls.incrementAndGet();

        if (failuresRemaining.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0) {
            responseObserver.onError(failureStatus.asRuntimeException());
            return;
        }

        ShipmentDeliveryInfo.Builder info = ShipmentDeliveryInfo.newBuilder()
                .setShipmentId(request.getShipmentId())
                .setStatus(hasLocation ? "IN_TRANSIT" : "WAITING_FOR_COURIER");

        if (hasLocation) {
            info.setHasLocation(true)
                    .setLatitude(50.4501)
                    .setLongitude(30.5234)
                    .setRecordedAt(Timestamp.newBuilder().setSeconds(1_794_000_000L));
        }

        responseObserver.onNext(info.build());
        responseObserver.onCompleted();
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
