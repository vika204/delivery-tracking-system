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
        handler.handle(event);
    }
}
