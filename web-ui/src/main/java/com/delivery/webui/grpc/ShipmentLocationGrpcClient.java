package com.delivery.webui.grpc;

import com.delivery.tracking.grpc.ShipmentLocationUpdate;
import com.delivery.tracking.grpc.ShipmentTrackingServiceGrpc;
import com.delivery.tracking.grpc.SubscribeToShipmentLocationsRequest;
import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(
        prefix = "tracking.stream",
        name = "enabled",
        havingValue = "true"
)
public class ShipmentLocationGrpcClient {

    private final ShipmentTrackingServiceGrpc.ShipmentTrackingServiceStub stub;

    public ShipmentLocationGrpcClient(
            ShipmentTrackingServiceGrpc.ShipmentTrackingServiceStub stub
    ) {
        this.stub = stub;
    }

    public Subscription subscribe(long shipmentId, Listener listener) {
        if (shipmentId <= 0) {
            throw new IllegalArgumentException("shipmentId must be positive");
        }

        Objects.requireNonNull(listener, "listener must not be null");

        SubscribeToShipmentLocationsRequest request =
                SubscribeToShipmentLocationsRequest.newBuilder()
                        .setShipmentId(shipmentId)
                        .build();
        Context.CancellableContext context =
                Context.current().withCancellation();
        ContextSubscription subscription = new ContextSubscription(context);

        try {
            context.run(() -> stub.subscribeToShipmentLocations(
                    request,
                    new StreamObserver<>() {
                        @Override
                        public void onNext(ShipmentLocationUpdate update) {
                            if (!subscription.isCancelled()) {
                                listener.onNext(update);
                            }
                        }

                        @Override
                        public void onError(Throwable error) {
                            if (!subscription.isCancelled()) {
                                listener.onError(error);
                            }
                        }

                        @Override
                        public void onCompleted() {
                            if (!subscription.isCancelled()) {
                                listener.onCompleted();
                            }
                        }
                    }
            ));
        } catch (RuntimeException ex) {
            subscription.cancel();
            throw ex;
        }

        return subscription;
    }

    public interface Listener {

        void onNext(ShipmentLocationUpdate update);

        void onError(Throwable error);

        void onCompleted();
    }

    @FunctionalInterface
    public interface Subscription {

        void cancel();
    }

    private static final class ContextSubscription implements Subscription {

        private final Context.CancellableContext context;
        private final AtomicBoolean cancelled = new AtomicBoolean();

        private ContextSubscription(Context.CancellableContext context) {
            this.context = context;
        }

        @Override
        public void cancel() {
            if (cancelled.compareAndSet(false, true)) {
                context.cancel(null);
            }
        }

        private boolean isCancelled() {
            return cancelled.get();
        }
    }
}
