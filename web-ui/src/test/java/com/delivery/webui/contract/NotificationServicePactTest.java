package com.delivery.webui.contract;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.DslPart;
import au.com.dius.pact.consumer.dsl.PactDslJsonArray;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import com.delivery.webui.client.NotificationClient;
import com.delivery.webui.client.dto.NotificationResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** web-ui (consumer) -> notification-service (provider): the user's notification feed. */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "notification-service", pactVersion = PactSpecVersion.V3)
class NotificationServicePactTest {

    private static final Map<String, String> JSON = Map.of("Content-Type", "application/json");

    @Pact(consumer = "web-ui", provider = "notification-service")
    RequestResponsePact feed(PactDslWithProvider builder) {
        DslPart feed = PactDslJsonArray.arrayMinLike(1)
                .integerType("notificationId", 1L)
                .integerType("shipmentId", 15L)
                .stringMatcher("type", "[A-Z_]+", "DELIVERED")
                .stringType("message", "Your parcel was delivered")
                .stringType("createdAt", "2026-11-12T12:02:00")
                .booleanType("read", false)
                .closeObject();
        return builder
                .given("user 42 has an unread notification 1")
                .uponReceiving("a notification feed read")
                .method("GET").path("/notifications/mine").query("limit=20").headers("X-User-Id", "42")
                .willRespondWith().status(200).headers(JSON).body(feed)
                .given("user 42 has an unread notification 1")
                .uponReceiving("an unread check")
                .method("GET").path("/notifications/mine/unread").headers("X-User-Id", "42")
                .willRespondWith().status(200).headers(JSON).body("true")
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "feed")
    void readsFeedAndUnreadFlag(MockServer server) {
        NotificationClient client = PactClients.create(NotificationClient.class, server.getUrl());

        List<NotificationResponse> feed = client.getMyNotifications(42, 20);
        assertThat(feed).isNotEmpty();
        assertThat(feed.getFirst().type()).isNotBlank();
        assertThat(feed.getFirst().message()).isNotBlank();

        assertThat(client.hasUnreadNotifications(42)).isTrue();
    }

    @Pact(consumer = "web-ui", provider = "notification-service")
    RequestResponsePact markRead(PactDslWithProvider builder) {
        return builder
                .given("user 42 has an unread notification 1")
                .uponReceiving("a mark-as-read request")
                .method("POST").path("/notifications/mine/read").query("throughId=1").headers("X-User-Id", "42")
                .willRespondWith().status(200).headers(JSON).body("false")
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "markRead")
    void marksNotificationsRead(MockServer server) {
        NotificationClient client = PactClients.create(NotificationClient.class, server.getUrl());

        assertThat(client.markNotificationsRead(42, 1)).isFalse();
    }

    @Pact(consumer = "web-ui", provider = "notification-service")
    RequestResponsePact badUser(PactDslWithProvider builder) {
        return builder
                .uponReceiving("a feed read with a non-positive user id")
                .method("GET").path("/notifications/mine").query("limit=20").headers("X-User-Id", "0")
                .willRespondWith().status(400)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "badUser")
    void rejectsNonPositiveUser(MockServer server) {
        NotificationClient client = PactClients.create(NotificationClient.class, server.getUrl());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.getMyNotifications(0, 20))
                .isInstanceOf(org.springframework.web.client.HttpClientErrorException.BadRequest.class);
    }
}
