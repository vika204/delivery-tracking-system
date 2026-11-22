package com.delivery.shipment.kafka;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

import com.delivery.shipment.event.DispatchEvent;

class EventValidationTest {

    private static DispatchEvent event(UUID id, String type, Long shipmentId, Long courierId) {
        return new DispatchEvent(id, type, Instant.now(), shipmentId, courierId, "Andrii", 3L, null, 42L, null);
    }

    @Test
    void assignmentWithACourierPasses() {
        assertThat(EventValidator.violations(event(UUID.randomUUID(), DispatchEvent.COURIER_ASSIGNED, 15L, 10L))).isNull();
        assertThat(EventValidator.violations(event(UUID.randomUUID(), DispatchEvent.COURIER_UNASSIGNED, 15L, 10L))).isNull();
    }

    @Test
    void anAssignmentChangeWithoutACourierIsInvalidButASearchDelayIsNot() {
        assertThat(EventValidator.violations(event(UUID.randomUUID(), DispatchEvent.COURIER_ASSIGNED, 15L, null)))
                .contains("courierId is required");
        assertThat(EventValidator.violations(event(UUID.randomUUID(), DispatchEvent.COURIER_SEARCH_DELAYED, 15L, null))).isNull();
    }

    @Test
    void identityFieldsAreChecked() {
        assertThat(EventValidator.violations(event(null, DispatchEvent.COURIER_ASSIGNED, 15L, 10L))).contains("eventId");
        assertThat(EventValidator.violations(event(UUID.randomUUID(), null, 15L, 10L))).contains("eventType");
        assertThat(EventValidator.violations(event(UUID.randomUUID(), DispatchEvent.COURIER_ASSIGNED, null, 10L))).contains("shipmentId");
        assertThat(EventValidator.violations(event(UUID.randomUUID(), DispatchEvent.COURIER_ASSIGNED, 15L, -5L))).contains("courierId");
    }
}
