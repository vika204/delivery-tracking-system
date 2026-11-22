package com.delivery.webui.contract;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import com.delivery.webui.client.UserProfileClient;
import com.delivery.webui.client.dto.UserProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;

import org.springframework.web.client.HttpClientErrorException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** web-ui (consumer) -> auth-service (provider): profile read and update. */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "auth-service", pactVersion = PactSpecVersion.V3)
class AuthServicePactTest {

    private static final Map<String, String> JSON = Map.of("Content-Type", "application/json");

    private static PactDslJsonBody profileBody() {
        return new PactDslJsonBody()
                .stringType("firstName", "Olena")
                .stringType("lastName", "Kovalenko")
                .stringType("email", "olena@example.com")
                .stringType("phone", "+380501234567");
    }

    @Pact(consumer = "web-ui", provider = "auth-service")
    RequestResponsePact profile(PactDslWithProvider builder) {
        return builder
                .given("user 42 exists")
                .uponReceiving("a profile read for user 42")
                .method("GET").path("/users/profile").headers("X-User-Id", "42")
                .willRespondWith().status(200).headers(JSON).body(profileBody())
                .given("user 42 exists")
                .uponReceiving("a profile update for user 42")
                .method("PUT").path("/users/profile")
                .headers(Map.of("X-User-Id", "42", "Content-Type", "application/json"))
                .body(profileBody())
                .willRespondWith().status(200).headers(JSON).body(profileBody())
                .uponReceiving("a profile read for an unknown user")
                .method("GET").path("/users/profile").headers("X-User-Id", "999")
                .willRespondWith().status(404)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "profile")
    void readsAndUpdatesProfile(MockServer server) {
        UserProfileClient client = PactClients.create(UserProfileClient.class, server.getUrl());

        UserProfile read = client.profile(42);
        assertThat(read.firstName()).isNotBlank();
        assertThat(read.email()).isNotBlank();

        UserProfile updated = client.updateProfile(42, new UserProfile("Olena", "Kovalenko", "olena@example.com", "+380501234567"));
        assertThat(updated.phone()).isNotBlank();

        assertThatThrownBy(() -> client.profile(999)).isInstanceOf(HttpClientErrorException.NotFound.class);
    }
}
