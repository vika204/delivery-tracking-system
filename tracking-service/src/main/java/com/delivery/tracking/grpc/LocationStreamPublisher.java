package com.delivery.tracking.grpc;

import com.delivery.tracking.event.LocationSavedEvent;
import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Component
public class LocationStreamPublisher {

    private static final Logger log = LoggerFactory.getLogger(LocationStreamPublisher.class);
    private final Map<Long, Set<ServerCallStreamObserver<ShipmentLocationUpdate>>> subscribers = new ConcurrentHashMap<>();
    private final TrackingProtoMapper mapper;
    private volatile boolean shuttingDown;

    public LocationStreamPublisher(TrackingProtoMapper mapper) {
        this.mapper = mapper;
    }

    boolean hasSubscribers(long shipmentId) {
        Set<ServerCallStreamObserver<ShipmentLocationUpdate>> active = subscribers.get(shipmentId);
        return active != null && !active.isEmpty();
    }

    public void subscribe(long shipmentId, ServerCallStreamObserver<ShipmentLocationUpdate> observer) {
        if (shuttingDown) {
            observer.onError(shutdownStatus().asRuntimeException());
            return;
        }
        subscribers.compute(shipmentId, (id, existing) -> {
            Set<ServerCallStreamObserver<ShipmentLocationUpdate>> active = existing;
            if (active == null) {
                active = ConcurrentHashMap.newKeySet();
            }
            active.add(observer);
            return active;
        });

        observer.setOnCancelHandler(() -> unsubscribe(shipmentId, observer));

        if (observer.isCancelled()) {
            unsubscribe(shipmentId, observer);
            return;
        }

        log.info("gRPC subscription started: shipmentId={}", shipmentId);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLocationSaved(LocationSavedEvent event) {
        Set<ServerCallStreamObserver<ShipmentLocationUpdate>> active = subscribers.get(event.shipmentId());

        if (active == null || active.isEmpty()) {
            return;
        }
        ShipmentLocationUpdate update;

        try {
            update = mapper.toProto(event);
        } catch (RuntimeException ex) {
            log.error("Failed to map location {} to Protobuf", event.locationId(), ex);
            return;
        }

        for (ServerCallStreamObserver<ShipmentLocationUpdate> observer : active) {
            try {
                synchronized (observer) {
                    if (observer.isCancelled() || subscribers.get(event.shipmentId()) != active) {
                        unsubscribe(event.shipmentId(), observer);
                    } else {
                        observer.onNext(update);
                    }
                }
            } catch (RuntimeException ex) {
                log.warn("Failed to send gRPC update: shipmentId={}", event.shipmentId(), ex);
                unsubscribe(event.shipmentId(), observer);
            }
        }

        log.info("Location update published: shipmentId={}, locationId={}", event.shipmentId(), event.locationId());
    }

    public void completeShipment(long shipmentId) {
        finish(shipmentId, ServerCallStreamObserver::onCompleted);
        log.info("gRPC streams completed for terminal shipment: shipmentId={}", shipmentId);
    }

    @EventListener(ContextClosedEvent.class)
    public void closeAllOnShutdown() {
        shuttingDown = true;
        int shipments = subscribers.size();
        for (Long shipmentId : Set.copyOf(subscribers.keySet())) {
            finish(shipmentId, observer -> observer.onError(shutdownStatus().asRuntimeException()));
        }
        log.info("gRPC location streams closed for shutdown: shipments={}", shipments);
    }

    private void finish(long shipmentId, Consumer<ServerCallStreamObserver<ShipmentLocationUpdate>> action) {
        Set<ServerCallStreamObserver<ShipmentLocationUpdate>> active = subscribers.remove(shipmentId);
        if (active == null) {
            return;
        }
        for (ServerCallStreamObserver<ShipmentLocationUpdate> observer : active) {
            synchronized (observer) {
                if (observer.isCancelled()) {
                    continue;
                }
                try {
                    action.accept(observer);
                } catch (RuntimeException ex) {
                    log.warn("Failed to finish gRPC stream: shipmentId={}", shipmentId, ex);
                }
            }
        }
    }

    private static Status shutdownStatus() {
        return Status.UNAVAILABLE.withDescription("tracking-service is shutting down");
    }

    private void unsubscribe(long shipmentId, ServerCallStreamObserver<ShipmentLocationUpdate> observer) {
        subscribers.computeIfPresent(shipmentId, (id, active) -> {
            if (active.remove(observer)) {
                log.info("gRPC subscription closed: shipmentId={}", shipmentId);
            }

            return active.isEmpty() ? null : active;
        });
    }
}