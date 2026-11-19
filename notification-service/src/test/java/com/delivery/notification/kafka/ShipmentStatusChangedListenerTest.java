package com.delivery.notification.kafka;

import com.delivery.events.ShipmentStatusChangedEvent;
import com.delivery.notification.handler.IdempotentShipmentEventHandler;
import com.delivery.notification.metrics.NotificationMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.kafka.support.converter.ByteArrayJacksonJsonMessageConverter;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ShipmentStatusChangedListenerTest {

    private final IdempotentShipmentEventHandler handler = mock(IdempotentShipmentEventHandler.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ShipmentStatusChangedListener listener = new ShipmentStatusChangedListener(handler, new NotificationMetrics(registry));

    @Test
    void readsExistingShipmentServiceWireContractIncludingSenderId() {
        String json = """
                {"eventId":"8c7b2f9e-1d3a-4c5b-9e8f-7a6b5c4d3e2f", "eventType":"SHIPMENT_STATUS_CHANGED",
                 "occurredAt":"2026-11-19T10:00:00Z", "shipmentId":15, "status":"DELIVERED",
                 "previousStatus":"IN_TRANSIT", "courierId":7, "actorType":"COURIER", "reason":null,
                 "senderId":42, "courierName":"Courier", "pickupLatitude":50.45, "pickupLongitude":30.52,
                 "deliveryLatitude":50.46, "deliveryLongitude":30.53}
                """;
        var record = new ConsumerRecord<>("shipment.status.changed", 0, 0L, "15", json.getBytes(StandardCharsets.UTF_8));
        var message = new ByteArrayJacksonJsonMessageConverter().toMessage(record, null, null, ShipmentStatusChangedEvent.class);
        var event = (ShipmentStatusChangedEvent) message.getPayload();

        listener.listen(event);

        assertThat(event.senderId()).isEqualTo(42L);
        assertThat(event.status()).isEqualTo("DELIVERED");
        verify(handler).handle(event);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void rejectsEventsWithoutValidSenderInsteadOfChoosingAnotherClient(Long senderId) {
        var event = new ShipmentStatusChangedEvent(UUID.randomUUID(), ShipmentStatusChangedEvent.TYPE,
                Instant.now(), 15L, "DELIVERED", senderId);

        assertThatThrownBy(() -> listener.listen(event)).isInstanceOf(InvalidShipmentEventException.class);
        verifyNoInteractions(handler);
    }

    @Test
    void rejectsWrongEventType() {
        var event = new ShipmentStatusChangedEvent(UUID.randomUUID(), "WRONG_TYPE", Instant.now(), 15L, "DELIVERED", 42L);

        assertThatThrownBy(() -> listener.listen(event)).isInstanceOf(InvalidShipmentEventException.class);
        verifyNoInteractions(handler);
    }

    @Test
    void countsDeadLetteredStatusNotifications() {
        listener.handleDlt(new ConsumerRecord<>("shipment.status.changed-dlt", 0, 1L, "15", new byte[0]));
        assertThat(registry.get("delivery.notifications.dlt").counter().count()).isEqualTo(1);
    }
}
