package com.delivery.notification.grpc;

import com.delivery.tracking.grpc.ShipmentLocationUpdate;
import com.delivery.tracking.grpc.ShipmentTrackingServiceGrpc;
import com.delivery.tracking.grpc.SubscribeToShipmentLocationsRequest;
import io.grpc.Context;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
public class ShipmentLocationGrpcClient {

    private static final Logger log = LoggerFactory.getLogger(ShipmentLocationGrpcClient.class);
    private final ShipmentTrackingServiceGrpc.ShipmentTrackingServiceStub stub;
    private final Map<Long, Subscription> subscriptions = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "grpc-location-retry");
        thread.setDaemon(true);
        return thread;
    });

    private final long initialDelayMs;
    private final long maxDelayMs;
    private final double multiplier;

    public ShipmentLocationGrpcClient(
            ShipmentTrackingServiceGrpc.ShipmentTrackingServiceStub stub,
            @Value("${tracking.grpc.reconnect.initial-delay-ms}") long initialDelayMs,
            @Value("${tracking.grpc.reconnect.max-delay-ms}") long maxDelayMs,
            @Value("${tracking.grpc.reconnect.multiplier}") double multiplier
    ) {
        this.stub = stub;
        this.initialDelayMs = initialDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.multiplier = multiplier;
    }

    public boolean subscribe(long shipmentId) {
        Subscription subscription = new Subscription(shipmentId, initialDelayMs);
        Subscription existing = subscriptions.putIfAbsent(shipmentId, subscription);

        if (existing != null) {
            return false;
        }

        openStream(subscription);
        return true;
    }

    private void openStream(Subscription subscription) {
        Context.CancellableContext context;
        int retryAttempt;

        synchronized (subscription) {
            if (subscription.stopped || subscriptions.get(subscription.shipmentId) != subscription) {
                return;
            }

            subscription.scheduledRetry = null;
            context = Context.current().withCancellation();
            subscription.context = context;
            retryAttempt = subscription.retryAttempt;
        }

        SubscribeToShipmentLocationsRequest request = SubscribeToShipmentLocationsRequest.newBuilder()
                        .setShipmentId(subscription.shipmentId)
                        .build();

        log.info("Starting gRPC subscription: shipmentId={}, retryAttempt={}", subscription.shipmentId, retryAttempt);

        try {
            context.run(() -> stub.subscribeToShipmentLocations(request, new StreamObserver<>() {

                        @Override
                        public void onNext(ShipmentLocationUpdate update) {
                            synchronized (subscription) {
                                if (subscription.stopped || subscription.context != context) {
                                    return;
                                }

                                subscription.retryAttempt = 0;
                                subscription.nextDelayMs = initialDelayMs;
                            }

                            Instant recordedAt = Instant.ofEpochSecond(update.getRecordedAt().getSeconds(), update.getRecordedAt().getNanos());
                            log.info("gRPC location received: shipmentId={}, locationId={}, latitude={}, longitude={}, recordedAt={}", update.getShipmentId(), update.getLocationId(), update.getLatitude(), update.getLongitude(), recordedAt);
                        }

                        @Override
                        public void onError(Throwable error) {
                            handleError(subscription, context, error);
                        }

                        @Override
                        public void onCompleted() {
                            log.info("gRPC subscription completed: shipmentId={}", subscription.shipmentId);
                            finish(subscription, context);
                        }
                    }
            ));
        } catch (RuntimeException ex) {
            handleError(subscription, context, ex);
        }
    }

    private void handleError(Subscription subscription, Context.CancellableContext context, Throwable error) {
        Status status = Status.fromThrowable(error);

        synchronized (subscription) {
            if (subscription.stopped || subscription.context != context) {
                return;
            }

            subscription.context = null;
            context.cancel(null);

            if (status.getCode() == Status.Code.CANCELLED) {
                log.info("gRPC subscription cancelled: shipmentId={}", subscription.shipmentId);
            } else {
                log.warn("gRPC subscription failed: shipmentId={}, status={}, description={}", subscription.shipmentId, status.getCode(), status.getDescription());
            }

            if (status.getCode() == Status.Code.UNAVAILABLE) {
                scheduleRetry(subscription);
            } else {
                subscription.stopped = true;
                subscriptions.remove(subscription.shipmentId, subscription);
            }
        }
    }

    private void scheduleRetry(Subscription subscription) {
        long delay = subscription.nextDelayMs;
        int attempt = ++subscription.retryAttempt;

        subscription.nextDelayMs = Math.min(maxDelayMs, (long) Math.ceil(delay * multiplier));

        log.info("gRPC retry scheduled: shipmentId={}, attempt={}, delay={}ms", subscription.shipmentId, attempt, delay);

        subscription.scheduledRetry = scheduler.schedule(
                () -> openStream(subscription),
                delay,
                TimeUnit.MILLISECONDS
        );
    }

    public boolean unsubscribe(long shipmentId) {
        Subscription subscription = subscriptions.remove(shipmentId);

        if (subscription == null) {
            return false;
        }

        synchronized (subscription) {
            subscription.stopped = true;

            if (subscription.scheduledRetry != null) {
                subscription.scheduledRetry.cancel(false);
                subscription.scheduledRetry = null;
            }

            if (subscription.context != null) {
                subscription.context.cancel(null);
                subscription.context = null;
            }
        }

        log.info("Stopped gRPC subscription: shipmentId={}", shipmentId);
        return true;
    }

    private void finish(Subscription subscription, Context.CancellableContext context) {
        synchronized (subscription) {
            if (subscription.stopped || subscription.context != context) {
                return;
            }

            subscription.stopped = true;
            subscription.context = null;
            subscriptions.remove(subscription.shipmentId, subscription);
        }

        context.cancel(null);
    }

    @PreDestroy
    public void shutdown() {
        for (Long shipmentId : subscriptions.keySet()) {
            unsubscribe(shipmentId);
        }

        scheduler.shutdownNow();
    }

    private static class Subscription {

        private final long shipmentId;
        private boolean stopped;
        private int retryAttempt;
        private long nextDelayMs;
        private Context.CancellableContext context;
        private ScheduledFuture<?> scheduledRetry;

        private Subscription(long shipmentId, long initialDelayMs) {
            this.shipmentId = shipmentId;
            this.nextDelayMs = initialDelayMs;
        }
    }
}