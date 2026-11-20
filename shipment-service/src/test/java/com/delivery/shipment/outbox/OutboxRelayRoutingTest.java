package com.delivery.shipment.outbox;

import com.delivery.shipment.config.KafkaTopicConfig;
import com.delivery.shipment.entity.OutboxEvent;
import com.delivery.shipment.event.ShipmentCreatedEvent;
import com.delivery.shipment.event.ShipmentDetailsUpdatedEvent;
import com.delivery.shipment.event.ShipmentStatusChangedEvent;

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

    @Test
    void shouldRouteDetailsUpdatesAwayFromShipmentEventsTopic() {
        OutboxEvent event = OutboxEvent.builder()
                .eventType(ShipmentDetailsUpdatedEvent.TYPE)
                .build();

        assertThat(OutboxRelay.topicFor(event)).isEqualTo(KafkaTopicConfig.SHIPMENT_DETAILS_UPDATED_TOPIC);
    }
}
