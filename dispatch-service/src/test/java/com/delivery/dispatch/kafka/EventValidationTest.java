package com.delivery.dispatch.kafka;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

import com.delivery.dispatch.event.ShipmentEventMessage;
import com.delivery.dispatch.event.ShipmentSnapshot;
import com.delivery.dispatch.event.ShipmentStatusChangedMessage;

import java.math.BigDecimal;

class EventValidationTest {

    private static ShipmentSnapshot snapshot(Long shipmentId, Double latitude, BigDecimal weight) {
        return new ShipmentSnapshot(shipmentId, 42L, "WAITING_FOR_COURIER", "Kyiv", "UA80000000000093317", latitude, 30.5,
                "Lviv", "UA46060250010015970", 49.8, 24.0, weight, new BigDecimal("540"), new BigDecimal("885"));
    }

    @Test
    void validShipmentEventPasses() {
        assertThat(EventValidator.violations(new ShipmentEventMessage(
                UUID.randomUUID(), "SHIPMENT_CREATED", Instant.now(), snapshot(15L, 50.4, new BigDecimal("2.5"))))).isNull();
    }

    @Test
    void shipmentEventNeedsIdTypeAndASaneSnapshot() {
        ShipmentSnapshot ok = snapshot(15L, 50.4, new BigDecimal("2.5"));
        assertThat(EventValidator.violations(new ShipmentEventMessage(null, "SHIPMENT_CREATED", Instant.now(), ok))).contains("eventId");
        assertThat(EventValidator.violations(new ShipmentEventMessage(UUID.randomUUID(), " ", Instant.now(), ok))).contains("eventType");
        assertThat(EventValidator.violations(new ShipmentEventMessage(UUID.randomUUID(), "SHIPMENT_CREATED", Instant.now(), null))).contains("payload");
        assertThat(EventValidator.violations(new ShipmentEventMessage(UUID.randomUUID(), "SHIPMENT_CREATED", Instant.now(),
                snapshot(null, 50.4, BigDecimal.ONE)))).contains("payload.shipmentId");
        assertThat(EventValidator.violations(new ShipmentEventMessage(UUID.randomUUID(), "SHIPMENT_CREATED", Instant.now(),
                snapshot(15L, 123.0, BigDecimal.ONE)))).contains("payload.pickupLatitude");
        assertThat(EventValidator.violations(new ShipmentEventMessage(UUID.randomUUID(), "SHIPMENT_CREATED", Instant.now(),
                snapshot(15L, 50.4, new BigDecimal("-1"))))).contains("payload.weight");
    }

    @Test
    void statusEventNeedsTypeShipmentAndStatus() {
        UUID id = UUID.randomUUID();
        assertThat(EventValidator.violations(new ShipmentStatusChangedMessage(id, "SHIPMENT_STATUS_CHANGED", Instant.now(), 15L,
                "IN_TRANSIT", "ASSIGNED", 10L, "COURIER", null))).isNull();
        assertThat(EventValidator.violations(new ShipmentStatusChangedMessage(null, "SHIPMENT_STATUS_CHANGED", null, 15L, "X", null, null, null, null))).contains("eventId");
        assertThat(EventValidator.violations(new ShipmentStatusChangedMessage(id, "OTHER", null, 15L, "X", null, null, null, null))).contains("eventType");
        assertThat(EventValidator.violations(new ShipmentStatusChangedMessage(id, "SHIPMENT_STATUS_CHANGED", null, null, "X", null, null, null, null))).contains("shipmentId");
        assertThat(EventValidator.violations(new ShipmentStatusChangedMessage(id, "SHIPMENT_STATUS_CHANGED", null, 15L, "", null, null, null, null))).contains("status");
    }
}
