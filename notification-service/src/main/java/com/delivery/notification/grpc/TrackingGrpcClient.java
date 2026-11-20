package com.delivery.notification.grpc;

import com.delivery.notification.dto.DeliveryInfoResponse;
import com.delivery.tracking.api.ProtoTimestamps;
import com.delivery.tracking.grpc.GetShipmentDeliveryInfoRequest;
import com.delivery.tracking.grpc.ShipmentDeliveryInfo;
import com.delivery.tracking.grpc.ShipmentLocationUpdate;
import com.delivery.tracking.grpc.ShipmentTrackingServiceGrpc;
import com.delivery.tracking.grpc.SubscribeToShipmentLocationsRequest;
import com.delivery.tracking.grpcclient.TrackingGrpcException;
import com.delivery.tracking.grpcclient.TrackingGrpcProperties;
import io.grpc.Context;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
public class TrackingGrpcClient {

    private static final Logger log = LoggerFactory.getLogger(TrackingGrpcClient.class);

    private final ShipmentTrackingServiceGrpc.ShipmentTrackingServiceBlockingStub blockingStub;
    private final ShipmentTrackingServiceGrpc.ShipmentTrackingServiceStub asyncStub;
    private final TrackingGrpcProperties properties;
    private final Map<Long, Subscription> subscriptions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("grpc-subscription-reconnect").daemon().factory());

    public TrackingGrpcClient(ShipmentTrackingServiceGrpc.ShipmentTrackingServiceBlockingStub blockingStub,
                              ShipmentTrackingServiceGrpc.ShipmentTrackingServiceStub asyncStub,
                              TrackingGrpcProperties properties) {
        this.blockingStub = blockingStub;
        this.asyncStub = asyncStub;
        this.properties = properties;
    }

    public DeliveryInfoResponse getDeliveryInfo(long shipmentId) {
        try {
            ShipmentDeliveryInfo info = blockingStub
                    .withDeadlineAfter(properties.deadline().toMillis(), TimeUnit.MILLISECONDS)
                    .getShipmentDeliveryInfo(GetShipmentDeliveryInfoRequest.newBuilder().setShipmentId(shipmentId).build());
            return toResponse(info);
        } catch (StatusRuntimeException ex) {
            throw new TrackingGrpcException(ex);
        }
    }

    public boolean subscribe(long shipmentId) {
        Subscription subscription = new Subscription(shipmentId);
        if (subscriptions.putIfAbsent(shipmentId, subscription) != null) {
            return false;
        }
        subscription.open();
        return true;
    }

    public boolean unsubscribe(long shipmentId) {
        Subscription subscription = subscriptions.remove(shipmentId);
        if (subscription == null) {
            return false;
        }
        subscription.close();
        return true;
    }

    @PreDestroy
    public void shutdown() {
        subscriptions.keySet().forEach(this::unsubscribe);
        reconnectScheduler.shutdownNow();
    }

    private static DeliveryInfoResponse toResponse(ShipmentDeliveryInfo info) {
        if (!info.getHasLocation()) {
            return new DeliveryInfoResponse(info.getShipmentId(), info.getStatus(), null, null, null);
        }
        return new DeliveryInfoResponse(
                info.getShipmentId(),
                info.getStatus(),
                info.getLatitude(),
                info.getLongitude(),
                ProtoTimestamps.toInstant(info.getRecordedAt())
        );
    }

    private final class Subscription implements StreamObserver<ShipmentLocationUpdate> {

        private final long shipmentId;
        private boolean closed;
        private int failedAttempts;
        private Context.CancellableContext context;
        private ScheduledFuture<?> reconnect;

        private Subscription(long shipmentId) {
            this.shipmentId = shipmentId;
        }

        synchronized void open() {
            if (closed) {
                return;
            }
            log.info("gRPC subscription opened: shipmentId={}, failedAttempts={}", shipmentId, failedAttempts);
            context = Context.current().withCancellation();
            SubscribeToShipmentLocationsRequest request = SubscribeToShipmentLocationsRequest.newBuilder()
                    .setShipmentId(shipmentId)
                    .build();
            context.run(() -> asyncStub.subscribeToShipmentLocations(request, this));
        }

        synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (reconnect != null) {
                reconnect.cancel(false);
            }
            if (context != null) {
                context.cancel(null);
            }
            log.info("gRPC subscription closed: shipmentId={}", shipmentId);
        }

        @Override
        public synchronized void onNext(ShipmentLocationUpdate update) {
            if (closed) {
                return;
            }
            failedAttempts = 0;
            log.info("gRPC location received: shipmentId={}, locationId={}, latitude={}, longitude={}, recordedAt={}",
                    update.getShipmentId(), update.getLocationId(), update.getLatitude(), update.getLongitude(),
                    ProtoTimestamps.toInstant(update.getRecordedAt()));
        }

        @Override
        public synchronized void onError(Throwable error) {
            if (closed) {
                return;
            }
            Status status = Status.fromThrowable(error);
            if (status.getCode() != Status.Code.UNAVAILABLE) {
                log.warn("gRPC subscription failed: shipmentId={}, status={}", shipmentId, status.getCode());
                finish();
                return;
            }
            Duration backoff = properties.retry().backoff(++failedAttempts);
            log.warn("gRPC stream interrupted, reconnecting: method=SubscribeToShipmentLocations, shipmentId={}, attempt={}, backoffMs={}",
                    shipmentId, failedAttempts + 1, backoff.toMillis());
            reconnect = reconnectScheduler.schedule(this::open, backoff.toMillis(), TimeUnit.MILLISECONDS);
        }

        @Override
        public synchronized void onCompleted() {
            if (closed) {
                return;
            }
            log.info("gRPC subscription completed by server: shipmentId={}", shipmentId);
            finish();
        }

        private void finish() {
            close();
            subscriptions.remove(shipmentId, this);
        }
    }
}
