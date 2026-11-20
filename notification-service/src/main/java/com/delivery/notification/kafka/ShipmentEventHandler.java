package com.delivery.notification.kafka;

import com.delivery.notification.event.ShipmentCreatedEvent;

public interface ShipmentEventHandler {

    void handle(ShipmentCreatedEvent event);
}