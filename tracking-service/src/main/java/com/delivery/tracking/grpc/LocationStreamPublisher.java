package com.delivery.tracking.grpc;

import com.delivery.tracking.event.LocationSavedEvent;
import io.grpc.stub.ServerCallStreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LocationStreamPublisher {

    private static final Logger log = LoggerFactory.getLogger(LocationStreamPublisher.class);
    private final Map<Long, Set<ServerCallStreamObserver<ShipmentLocationUpdate>>> subscribers = new ConcurrentHashMap<>();
    private final TrackingProtoMapper mapper;

    public LocationStreamPublisher(TrackingProtoMapper mapper) {
        this.mapper = mapper;
    }

    public void subscribe(long shipmentId, ServerCallStreamObserver<ShipmentLocationUpdate> observer) {
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
                    observer.onCompleted();
                } catch (RuntimeException ex) {
                    log.warn("Failed to complete gRPC stream: shipmentId={}", shipmentId, ex);
                }
            }
        }

        log.info("gRPC streams completed for terminal shipment: shipmentId={}", shipmentId);
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