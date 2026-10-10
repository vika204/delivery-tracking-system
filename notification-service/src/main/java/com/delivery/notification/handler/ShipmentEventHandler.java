package com.delivery.notification.handler;

import com.delivery.events.ShipmentCreatedEvent;

public interface ShipmentEventHandler {

    void handle(ShipmentCreatedEvent event);
}