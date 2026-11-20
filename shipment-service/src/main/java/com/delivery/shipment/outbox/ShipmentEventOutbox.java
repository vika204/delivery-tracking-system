package com.delivery.shipment.outbox;

import com.delivery.shipment.entity.ActorType;
import com.delivery.shipment.entity.OutboxEvent;
import com.delivery.shipment.entity.Shipment;
import com.delivery.shipment.entity.ShipmentStatus;
import com.delivery.shipment.event.ShipmentCreatedEvent;
import com.delivery.shipment.event.ShipmentDetailsUpdatedEvent;
import com.delivery.shipment.event.ShipmentPayload;
import com.delivery.shipment.event.ShipmentStatusChangedEvent;
import com.delivery.shipment.repository.OutboxEventRepository;

import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Component
public class ShipmentEventOutbox {

    private final OutboxEventRepository outboxRepository;
    private final JsonMapper jsonMapper;
    private final OutboxTraceContext traceContext;
    private final Clock clock;

    public ShipmentEventOutbox(OutboxEventRepository outboxRepository, JsonMapper jsonMapper,
                               OutboxTraceContext traceContext, Clock clock) {
        this.outboxRepository = outboxRepository;
        this.jsonMapper = jsonMapper;
        this.traceContext = traceContext;
        this.clock = clock;
    }

    public void shipmentCreated(Shipment shipment) {
        ShipmentCreatedEvent event = new ShipmentCreatedEvent(
                UUID.randomUUID(), ShipmentCreatedEvent.TYPE, Instant.now(clock), payload(shipment));
        save(event.eventId(), shipment, event.eventType(), event);
    }

    public void statusChanged(Shipment shipment, ShipmentStatus previousStatus, Long courierId, String courierName,
                              ActorType actorType, String reason) {
        ShipmentStatusChangedEvent event = new ShipmentStatusChangedEvent(
                UUID.randomUUID(),
                ShipmentStatusChangedEvent.TYPE,
                Instant.now(clock),
                shipment.getShipmentId(),
                shipment.getStatus().name(),
                previousStatus == null ? null : previousStatus.name(),
                courierId,
                actorType.name(),
                reason,
                shipment.getUserId(),
                courierName,
                shipment.getPickupLatitude(),
                shipment.getPickupLongitude(),
                shipment.getDeliveryLatitude(),
                shipment.getDeliveryLongitude()
        );
        save(event.eventId(), shipment, event.eventType(), event);
    }

    public void detailsUpdated(Shipment shipment, boolean priceRecalculated) {
        ShipmentDetailsUpdatedEvent event = new ShipmentDetailsUpdatedEvent(
                UUID.randomUUID(), ShipmentDetailsUpdatedEvent.TYPE, Instant.now(clock),
                payload(shipment), priceRecalculated);
        save(event.eventId(), shipment, event.eventType(), event);
    }

    static ShipmentPayload payload(Shipment shipment) {
        return new ShipmentPayload(
                shipment.getShipmentId(),
                shipment.getUserId(),
                shipment.getStatus().name(),
                shipment.getPickupAddress(),
                shipment.getPickupCityCode(),
                shipment.getPickupLatitude(),
                shipment.getPickupLongitude(),
                shipment.getDeliveryAddress(),
                shipment.getDeliveryCityCode(),
                shipment.getDeliveryLatitude(),
                shipment.getDeliveryLongitude(),
                shipment.getWeight(),
                shipment.getDistance(),
                shipment.getPrice(),
                shipment.getDeliveryType() == null ? null : shipment.getDeliveryType().name()
        );
    }

    private void save(UUID eventId, Shipment shipment, String eventType, Object event) {
        outboxRepository.save(OutboxEvent.builder()
                .eventId(eventId)
                .aggregateType("Shipment")
                .aggregateId(shipment.getShipmentId().toString())
                .eventType(eventType)
                .payload(jsonMapper.writeValueAsString(event))
                .traceparent(traceContext.capture())
                .published(false)
                .build());
    }
}
