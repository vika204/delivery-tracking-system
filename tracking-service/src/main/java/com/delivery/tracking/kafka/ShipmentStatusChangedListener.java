package com.delivery.tracking.kafka;

import com.delivery.tracking.grpc.LocationStreamPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ShipmentStatusChangedListener {

    private static final Logger log = LoggerFactory.getLogger(ShipmentStatusChangedListener.class);
    private final JsonMapper jsonMapper;
    private final LocationStreamPublisher publisher;

    public ShipmentStatusChangedListener(JsonMapper jsonMapper, LocationStreamPublisher publisher) {
        this.jsonMapper = jsonMapper;
        this.publisher = publisher;
    }

    @KafkaListener(topics = "shipment.status.changed", groupId = "shipment-tracking-group")
    public void onStatusChanged(String json) {
        ShipmentStatusChangedEvent event = jsonMapper.readValue(json, ShipmentStatusChangedEvent.class);

        if (event.eventId() == null || event.occurredAt() == null || event.status() == null
                || event.shipmentId() == null || event.shipmentId() <= 0
                || !"SHIPMENT_STATUS_CHANGED".equals(event.eventType())) {
            throw new IllegalArgumentException("Invalid shipment status event");
        }

        log.info("Shipment status changed: shipmentId={}, status={}", event.shipmentId(), event.status());

        if ("DELIVERED".equals(event.status()) || "CANCELLED".equals(event.status())) {
            publisher.completeShipment(event.shipmentId());
        }
    }
}