package com.delivery.notification.kafka;

import com.delivery.events.ShipmentCreatedEvent;
import com.delivery.notification.handler.ShipmentEventHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ShipmentEventListener {

    private final ShipmentEventHandler handler;

    public ShipmentEventListener(ShipmentEventHandler handler) {
        this.handler = handler;
    }

    @KafkaListener(
            topics = "shipment.events",
            groupId = "shipment-notification-group"
    )
    public void listen(ShipmentCreatedEvent event) {
        validate(event);
        handler.handle(event);
    }

    private void validate(ShipmentCreatedEvent event) {
        if (event.eventId() == null) {
            throw new InvalidShipmentEventException("Shipment event has no eventId");
        }
        if (event.payload() == null || event.payload().shipmentId() == null) {
            throw new InvalidShipmentEventException("Shipment event " + event.eventId() + " has no shipmentId");
        }
    }
}
