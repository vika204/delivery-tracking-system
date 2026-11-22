package com.delivery.dispatch.contract;

import au.com.dius.pact.consumer.MessagePactBuilder;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.consumer.junit5.ProviderType;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.annotations.Pact;
import au.com.dius.pact.core.model.annotations.PactDirectory;
import au.com.dius.pact.core.model.messaging.Message;
import au.com.dius.pact.core.model.messaging.MessagePact;
import com.delivery.dispatch.event.ShipmentEventMessage;
import com.delivery.dispatch.event.ShipmentStatusChangedMessage;
import com.delivery.dispatch.kafka.ShipmentEventProcessor;
import com.delivery.dispatch.kafka.ShipmentEventsListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * dispatch-service (consumer) <- shipment-service (provider): everything dispatch needs to find a courier and to
 * react to cancellations / pickups, pushed through the real listener.
 */
@ExtendWith(PactConsumerTestExt.class)
@PactDirectory("../pacts/messages")
@PactTestFor(providerName = "shipment-service", providerType = ProviderType.ASYNCH, pactVersion = PactSpecVersion.V3)
class ShipmentEventsPactTest {

    private static final String INSTANT = "\\d{4}-\\d{2}-\\d{2}T[\\d:.]+Z";

    private static PactDslJsonBody snapshotEvent(String type) {
        return new PactDslJsonBody()
                .uuid("eventId")
                .stringValue("eventType", type)
                .stringMatcher("occurredAt", INSTANT, "2026-11-22T12:00:00Z")
                .object("payload")
                .integerType("shipmentId", 15L)
                .integerType("userId", 42L)
                .stringType("status", "WAITING_FOR_COURIER")
                .stringType("pickupAddress", "Kyiv, Khreshchatyk 1")
                .stringType("pickupCityCode", "UA80000000000093317")
                .decimalType("pickupLatitude", 50.4501)
                .decimalType("pickupLongitude", 30.5234)
                .stringType("deliveryAddress", "Lviv, Svobody 10")
                .stringType("deliveryCityCode", "UA46060250010015970")
                .decimalType("deliveryLatitude", 49.8397)
                .decimalType("deliveryLongitude", 24.0297)
                .decimalType("weight", 2.5)
                .decimalType("distance", 540.0)
                .decimalType("price", 885.0)
                .closeObject().asBody();
    }

    private static PactDslJsonBody statusEvent(String status) {
        return new PactDslJsonBody()
                .uuid("eventId")
                .stringValue("eventType", "SHIPMENT_STATUS_CHANGED")
                .stringMatcher("occurredAt", INSTANT, "2026-11-22T12:00:00Z")
                .integerType("shipmentId", 15L)
                .stringType("status", status)
                .stringType("previousStatus", "ASSIGNED")
                .integerType("courierId", 10L)
                .stringType("actorType", "COURIER");
    }

    @Pact(consumer = "dispatch-service", provider = "shipment-service")
    MessagePact shipmentEvents(MessagePactBuilder builder) {
        return builder
                .expectsToReceive("a SHIPMENT_CREATED event").withContent(snapshotEvent("SHIPMENT_CREATED"))
                .expectsToReceive("a SHIPMENT_DETAILS_UPDATED event").withContent(snapshotEvent("SHIPMENT_DETAILS_UPDATED"))
                .expectsToReceive("a SHIPMENT_STATUS_CHANGED event to IN_TRANSIT").withContent(statusEvent("IN_TRANSIT"))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "shipmentEvents")
    void listenerHandsEveryMessageToTheProcessor(List<Message> messages) {
        assertThat(messages).hasSize(3);
        ShipmentEventProcessor processor = mock(ShipmentEventProcessor.class);
        JsonMapper mapper = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        ShipmentEventsListener listener = new ShipmentEventsListener(mapper, processor);

        listener.onShipmentEvent(messages.get(0).contentsAsString());
        listener.onShipmentEvent(messages.get(1).contentsAsString());
        listener.onShipmentStatusChanged(messages.get(2).contentsAsString());

        ArgumentCaptor<ShipmentEventMessage> snapshots = ArgumentCaptor.forClass(ShipmentEventMessage.class);
        verify(processor, atLeastOnce()).process(snapshots.capture());
        assertThat(snapshots.getAllValues()).extracting(ShipmentEventMessage::eventType)
                .containsExactly("SHIPMENT_CREATED", "SHIPMENT_DETAILS_UPDATED");
        assertThat(snapshots.getAllValues().getFirst().payload().deliveryLatitude()).isNotNull();
        assertThat(snapshots.getAllValues().getFirst().payload().weight()).isNotNull();

        ArgumentCaptor<ShipmentStatusChangedMessage> statuses = ArgumentCaptor.forClass(ShipmentStatusChangedMessage.class);
        verify(processor).process(statuses.capture());
        assertThat(statuses.getValue().status()).isEqualTo("IN_TRANSIT");
        assertThat(statuses.getValue().courierId()).isEqualTo(10);
    }
}
