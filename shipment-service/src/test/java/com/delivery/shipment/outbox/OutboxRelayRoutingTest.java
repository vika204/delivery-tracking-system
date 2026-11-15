package com.delivery.shipment.outbox;

import com.delivery.events.ShipmentCreatedEvent;
import com.delivery.events.ShipmentDetailsUpdatedEvent;
import com.delivery.events.ShipmentStatusChangedEvent;
import com.delivery.shipment.config.KafkaTopicConfig;
import com.delivery.shipment.entity.OutboxEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxRelayRoutingTest {

    @Test
    void shouldKeepShipmentCreatedEventsOnOriginalTopic() {
        OutboxEvent event = OutboxEvent.builder()
                .eventType(ShipmentCreatedEvent.TYPE)
                .build();

        assertThat(OutboxRelay.topicFor(event)).isEqualTo(KafkaTopicConfig.SHIPMENT_EVENTS_TOPIC);
    }

    @Test
    void shouldRouteStatusChangesToSeparateTopic() {
        OutboxEvent event = OutboxEvent.builder()
                .eventType(ShipmentStatusChangedEvent.TYPE)
                .build();

        assertThat(OutboxRelay.topicFor(event)).isEqualTo(KafkaTopicConfig.SHIPMENT_STATUS_CHANGED_TOPIC);
    }

    // notification-service читає все з shipment.events як SHIPMENT_CREATED, тож оновлення йдуть окремо
    @Test
    void shouldRouteDetailsUpdatesAwayFromShipmentEventsTopic() {
        OutboxEvent event = OutboxEvent.builder()
                .eventType(ShipmentDetailsUpdatedEvent.TYPE)
                .build();

        assertThat(OutboxRelay.topicFor(event)).isEqualTo(KafkaTopicConfig.SHIPMENT_DETAILS_UPDATED_TOPIC);
    }
}
