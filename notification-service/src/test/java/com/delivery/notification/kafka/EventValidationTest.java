package com.delivery.notification.kafka;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

import com.delivery.notification.event.ShipmentCreatedEvent;
import com.delivery.notification.event.ShipmentPayload;
import com.delivery.notification.event.ShipmentStatusChangedEvent;

class EventValidationTest {

    @Test
    void validCreatedEventPasses() {
        assertThat(EventValidator.violations(new ShipmentCreatedEvent(
                UUID.randomUUID(), "SHIPMENT_CREATED", Instant.now(), new ShipmentPayload(15L, 42L, "WAITING_FOR_COURIER")))).isNull();
    }

    @Test
    void createdEventNeedsIdAndShipment() {
        assertThat(EventValidator.violations(new ShipmentCreatedEvent(null, "SHIPMENT_CREATED", Instant.now(),
                new ShipmentPayload(15L, 42L, "X")))).contains("eventId");
        assertThat(EventValidator.violations(new ShipmentCreatedEvent(UUID.randomUUID(), "SHIPMENT_CREATED", Instant.now(), null)))
                .contains("payload");
        assertThat(EventValidator.violations(new ShipmentCreatedEvent(UUID.randomUUID(), "SHIPMENT_CREATED", Instant.now(),
                new ShipmentPayload(null, 42L, "X")))).contains("payload.shipmentId");
    }

    @Test
    void statusEventNeedsEverythingNotificationUses() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        assertThat(EventValidator.violations(new ShipmentStatusChangedEvent(id, "SHIPMENT_STATUS_CHANGED", now, 15L, "DELIVERED", 42L))).isNull();
        assertThat(EventValidator.violations(new ShipmentStatusChangedEvent(null, "SHIPMENT_STATUS_CHANGED", now, 15L, "X", 42L))).contains("eventId");
        assertThat(EventValidator.violations(new ShipmentStatusChangedEvent(id, "NOPE", now, 15L, "X", 42L))).contains("eventType");
        assertThat(EventValidator.violations(new ShipmentStatusChangedEvent(id, "SHIPMENT_STATUS_CHANGED", null, 15L, "X", 42L))).contains("occurredAt");
        assertThat(EventValidator.violations(new ShipmentStatusChangedEvent(id, "SHIPMENT_STATUS_CHANGED", now, -1L, "X", 42L))).contains("shipmentId");
        assertThat(EventValidator.violations(new ShipmentStatusChangedEvent(id, "SHIPMENT_STATUS_CHANGED", now, 15L, null, 42L))).contains("status");
        assertThat(EventValidator.violations(new ShipmentStatusChangedEvent(id, "SHIPMENT_STATUS_CHANGED", now, 15L, "X", null))).contains("senderId");
    }
}
