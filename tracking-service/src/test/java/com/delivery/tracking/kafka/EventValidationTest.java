package com.delivery.tracking.kafka;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

import com.delivery.tracking.event.ShipmentStatusChangedEvent;

class EventValidationTest {

    private static ShipmentStatusChangedEvent event(UUID id, String type, Instant at, Long shipment, String status) {
        return new ShipmentStatusChangedEvent(id, type, at, shipment, status);
    }

    @Test
    void validEventHasNoViolations() {
        assertThat(EventValidator.violations(
                event(UUID.randomUUID(), "SHIPMENT_STATUS_CHANGED", Instant.now(), 15L, "DELIVERED"))).isNull();
    }

    @Test
    void everyRequiredFieldIsChecked() {
        assertThat(EventValidator.violations(event(null, "SHIPMENT_STATUS_CHANGED", Instant.now(), 15L, "X"))).contains("eventId");
        assertThat(EventValidator.violations(event(UUID.randomUUID(), "OTHER", Instant.now(), 15L, "X"))).contains("eventType");
        assertThat(EventValidator.violations(event(UUID.randomUUID(), "SHIPMENT_STATUS_CHANGED", null, 15L, "X"))).contains("occurredAt");
        assertThat(EventValidator.violations(event(UUID.randomUUID(), "SHIPMENT_STATUS_CHANGED", Instant.now(), null, "X"))).contains("shipmentId");
        assertThat(EventValidator.violations(event(UUID.randomUUID(), "SHIPMENT_STATUS_CHANGED", Instant.now(), 0L, "X"))).contains("shipmentId");
        assertThat(EventValidator.violations(event(UUID.randomUUID(), "SHIPMENT_STATUS_CHANGED", Instant.now(), 15L, " "))).contains("status");
    }
}
