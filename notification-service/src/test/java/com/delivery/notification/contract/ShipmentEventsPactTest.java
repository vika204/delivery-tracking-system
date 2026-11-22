package com.delivery.notification.contract;

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
import com.delivery.notification.event.ShipmentCreatedEvent;
import com.delivery.notification.event.ShipmentStatusChangedEvent;
import com.delivery.notification.kafka.IdempotentShipmentEventHandler;
import com.delivery.notification.kafka.ShipmentEventHandler;
import com.delivery.notification.kafka.ShipmentEventListener;
import com.delivery.notification.kafka.ShipmentStatusChangedListener;
import com.delivery.notification.metrics.NotificationMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * notification-service (consumer) <- shipment-service (provider) over Kafka. The recorded messages are pushed
 * through the real listeners, so a field the listener needs but the provider stops sending fails here.
 */
@ExtendWith(PactConsumerTestExt.class)
@PactDirectory("../pacts/messages")
@PactTestFor(providerName = "shipment-service", providerType = ProviderType.ASYNCH, pactVersion = PactSpecVersion.V3)
class ShipmentEventsPactTest {

    private static final String INSTANT = "\\d{4}-\\d{2}-\\d{2}T[\\d:.]+Z";
    private final JsonMapper mapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    private final NotificationMetrics metrics = new NotificationMetrics(new SimpleMeterRegistry());

    @Pact(consumer = "notification-service", provider = "shipment-service")
    MessagePact shipmentEvents(MessagePactBuilder builder) {
        PactDslJsonBody created = new PactDslJsonBody()
                .uuid("eventId")
                .stringValue("eventType", "SHIPMENT_CREATED")
                .stringMatcher("occurredAt", INSTANT, "2026-11-22T12:00:00Z")
                .object("payload")
                .integerType("shipmentId", 15L)
                .integerType("userId", 42L)
                .stringType("status", "WAITING_FOR_COURIER")
                .closeObject().asBody();
        PactDslJsonBody statusChanged = new PactDslJsonBody()
                .uuid("eventId")
                .stringValue("eventType", "SHIPMENT_STATUS_CHANGED")
                .stringMatcher("occurredAt", INSTANT, "2026-11-22T12:00:00Z")
                .integerType("shipmentId", 15L)
                .stringType("status", "DELIVERED")
                .integerType("senderId", 42L);
        return builder
                .expectsToReceive("a SHIPMENT_CREATED event").withContent(created)
                .expectsToReceive("a SHIPMENT_STATUS_CHANGED event to DELIVERED").withContent(statusChanged)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "shipmentEvents")
    void listenersAcceptTheProvidersMessages(List<Message> messages) {
        assertThat(messages).hasSize(2);

        ShipmentEventHandler createdHandler = mock(ShipmentEventHandler.class);
        ShipmentCreatedEvent created = mapper.readValue(messages.get(0).contentsAsBytes(), ShipmentCreatedEvent.class);
        new ShipmentEventListener(createdHandler, metrics).listen(created);
        ArgumentCaptor<ShipmentCreatedEvent> createdCaptor = ArgumentCaptor.forClass(ShipmentCreatedEvent.class);
        verify(createdHandler).handle(createdCaptor.capture());
        assertThat(createdCaptor.getValue().payload().shipmentId()).isEqualTo(15);
        assertThat(createdCaptor.getValue().payload().userId()).isEqualTo(42);

        IdempotentShipmentEventHandler statusHandler = mock(IdempotentShipmentEventHandler.class);
        ShipmentStatusChangedEvent changed = mapper.readValue(messages.get(1).contentsAsBytes(), ShipmentStatusChangedEvent.class);
        new ShipmentStatusChangedListener(statusHandler, metrics).listen(changed);
        ArgumentCaptor<ShipmentStatusChangedEvent> changedCaptor = ArgumentCaptor.forClass(ShipmentStatusChangedEvent.class);
        verify(statusHandler).handle(changedCaptor.capture());
        assertThat(changedCaptor.getValue().senderId()).isEqualTo(42);
        assertThat(changedCaptor.getValue().status()).isEqualTo("DELIVERED");
    }
}
