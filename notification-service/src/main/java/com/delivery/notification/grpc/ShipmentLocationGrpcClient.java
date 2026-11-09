package com.delivery.notification.grpc;

import com.delivery.tracking.grpc.ShipmentLocationUpdate;
import com.delivery.tracking.grpc.ShipmentTrackingServiceGrpc;
import com.delivery.tracking.grpc.SubscribeToShipmentLocationsRequest;
import io.grpc.Context;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ShipmentLocationGrpcClient {

    private static final Logger log = LoggerFactory.getLogger(ShipmentLocationGrpcClient.class);
    private final ShipmentTrackingServiceGrpc.ShipmentTrackingServiceStub stub;
    private final Map<Long, Context.CancellableContext> subscriptions = new ConcurrentHashMap<>();

    public ShipmentLocationGrpcClient(ShipmentTrackingServiceGrpc.ShipmentTrackingServiceStub stub) {
        this.stub = stub;
    }

    public boolean subscribe(long shipmentId) {
        Context.CancellableContext context = Context.current().withCancellation();
        Context.CancellableContext existing = subscriptions.putIfAbsent(shipmentId, context);

        if (existing != null) {
            context.cancel(null);
            return false;
        }

        SubscribeToShipmentLocationsRequest request =
                SubscribeToShipmentLocationsRequest.newBuilder()
                        .setShipmentId(shipmentId)
                        .build();

        log.info("Starting gRPC subscription: shipmentId={}", shipmentId);

        try {
            context.run(() -> stub.subscribeToShipmentLocations(request, new StreamObserver<>() {

                        @Override
                        public void onNext(ShipmentLocationUpdate update) {
                            Instant recordedAt = Instant.ofEpochSecond(update.getRecordedAt().getSeconds(), update.getRecordedAt().getNanos());
                            log.info("gRPC location received: shipmentId={}, locationId={}, latitude={}, longitude={}, recordedAt={}", update.getShipmentId(), update.getLocationId(), update.getLatitude(), update.getLongitude(), recordedAt);
                        }

                        @Override
                        public void onError(Throwable error) {
                            Status status = Status.fromThrowable(error);

                            if (status.getCode() == Status.Code.CANCELLED) {
                                log.info("gRPC subscription cancelled: shipmentId={}", shipmentId);
                            } else {
                                log.warn("gRPC subscription failed: shipmentId={}, status={}, description={}", shipmentId, status.getCode(), status.getDescription());
                            }
                            finish(shipmentId, context);
                        }

                        @Override
                        public void onCompleted() {
                            log.info("gRPC subscription completed: shipmentId={}", shipmentId);
                            finish(shipmentId, context);
                        }
                    }
            ));
        } catch (RuntimeException ex) {
            finish(shipmentId, context);
            throw ex;
        }

        return true;
    }

    public boolean unsubscribe(long shipmentId) {
        Context.CancellableContext context = subscriptions.remove(shipmentId);
        if (context == null) {
            return false;
        }
        context.cancel(null);
        log.info("Stopped gRPC subscription: shipmentId={}", shipmentId);
        return true;
    }

    private void finish(long shipmentId, Context.CancellableContext context) {
        subscriptions.remove(shipmentId, context);
        context.cancel(null);
    }
}