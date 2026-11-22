package com.delivery.shipment.contract;

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
import com.delivery.shipment.event.DispatchEvent;
import com.delivery.shipment.kafka.DispatchEventListener;
import com.delivery.shipment.service.ShipmentLifecycleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** shipment-service (consumer) <- dispatch-service (provider): courier assignment events that drive the shipment. */
@ExtendWith(PactConsumerTestExt.class)
@PactDirectory("../pacts/messages")
@PactTestFor(providerName = "dispatch-service", providerType = ProviderType.ASYNCH, pactVersion = PactSpecVersion.V3)
class DispatchEventsPactTest {

    private static final String INSTANT = "\\d{4}-\\d{2}-\\d{2}T[\\d:.]+Z";
    private final JsonMapper mapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private static PactDslJsonBody assignment(String type) {
        return new PactDslJsonBody()
                .uuid("eventId")
                .stringValue("eventType", type)
                .stringMatcher("occurredAt", INSTANT, "2026-11-22T12:00:00Z")
                .integerType("shipmentId", 15L)
                .integerType("courierId", 10L)
                .stringType("courierName", "Andrii Kovalenko")
                .integerType("assignmentId", 3L);
    }

    @Pact(consumer = "shipment-service", provider = "dispatch-service")
    MessagePact dispatchEvents(MessagePactBuilder builder) {
        return builder
                .expectsToReceive("a COURIER_ASSIGNED event").withContent(assignment("COURIER_ASSIGNED"))
                .expectsToReceive("a COURIER_UNASSIGNED event").withContent(assignment("COURIER_UNASSIGNED")
                        .stringType("reason", "COURIER_WITHDREW"))
                .expectsToReceive("a COURIER_SEARCH_DELAYED event").withContent(new PactDslJsonBody()
                        .uuid("eventId")
                        .stringValue("eventType", "COURIER_SEARCH_DELAYED")
                        .stringMatcher("occurredAt", INSTANT, "2026-11-22T12:00:00Z")
                        .integerType("shipmentId", 15L)
                        .stringType("reason", "NO_COURIER_AVAILABLE")
                        .integerType("senderId", 42L))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "dispatchEvents")
    void assignmentEventsDriveTheLifecycleAndOthersAreIgnored(List<Message> messages) {
        assertThat(messages).hasSize(3);
        ShipmentLifecycleService lifecycle = mock(ShipmentLifecycleService.class);
        DispatchEventListener listener = new DispatchEventListener(mapper, lifecycle);

        listener.onDispatchEvent(messages.get(0).contentsAsString());
        listener.onDispatchEvent(messages.get(1).contentsAsString());
        listener.onDispatchEvent(messages.get(2).contentsAsString());

        ArgumentCaptor<DispatchEvent> handled = ArgumentCaptor.forClass(DispatchEvent.class);
        verify(lifecycle, org.mockito.Mockito.times(2)).onDispatchEvent(handled.capture());
        assertThat(handled.getAllValues()).extracting(DispatchEvent::eventType)
                .containsExactly("COURIER_ASSIGNED", "COURIER_UNASSIGNED");
        assertThat(handled.getAllValues().getFirst().courierId()).isEqualTo(10);
        assertThat(handled.getAllValues().getFirst().courierName()).isNotBlank();
    }
}
