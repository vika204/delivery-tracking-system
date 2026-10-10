package com.delivery.notification.handler;

import com.delivery.events.ShipmentCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class StubShipmentEventHandler implements ShipmentEventHandler {

    private static final Logger log = LoggerFactory.getLogger(StubShipmentEventHandler.class);

    @Override
    public void handle(ShipmentCreatedEvent event) {
        log.info("STUB: received shipment event: {}", event.eventId());
    }
}
