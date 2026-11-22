package com.delivery.tracking.contract;

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
import com.delivery.tracking.grpc.LocationStreamPublisher;
import com.delivery.tracking.kafka.ShipmentStatusChangedListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** tracking-service (consumer) <- shipment-service (provider): terminal statuses close the live location stream. */
@ExtendWith(PactConsumerTestExt.class)
@PactDirectory("../pacts/messages")
@PactTestFor(providerName = "shipment-service", providerType = ProviderType.ASYNCH, pactVersion = PactSpecVersion.V3)
class ShipmentStatusEventPactTest {

    private static final String INSTANT = "\\d{4}-\\d{2}-\\d{2}T[\\d:.]+Z";

    private static PactDslJsonBody statusEvent(String status) {
        return new PactDslJsonBody()
                .uuid("eventId")
                .stringValue("eventType", "SHIPMENT_STATUS_CHANGED")
                .stringMatcher("occurredAt", INSTANT, "2026-11-22T12:00:00Z")
                .integerType("shipmentId", 15L)
                .stringType("status", status);
    }

    @Pact(consumer = "tracking-service-events", provider = "shipment-service")
    MessagePact statusEvents(MessagePactBuilder builder) {
        return builder
                .expectsToReceive("a SHIPMENT_STATUS_CHANGED event to DELIVERED").withContent(statusEvent("DELIVERED"))
                .expectsToReceive("a SHIPMENT_STATUS_CHANGED event to IN_TRANSIT").withContent(statusEvent("IN_TRANSIT"))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "statusEvents")
    void deliveredClosesTheStreamAndInTransitDoesNot(List<Message> messages) {
        assertThat(messages).hasSize(2);
        LocationStreamPublisher publisher = mock(LocationStreamPublisher.class);
        JsonMapper mapper = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        ShipmentStatusChangedListener listener = new ShipmentStatusChangedListener(mapper, publisher);

        listener.onStatusChanged(messages.get(1).contentsAsString());
        verify(publisher, never()).completeShipment(15L);

        listener.onStatusChanged(messages.get(0).contentsAsString());
        verify(publisher).completeShipment(15L);
    }
}
