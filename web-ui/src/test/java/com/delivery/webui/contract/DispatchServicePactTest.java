package com.delivery.webui.contract;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.DslPart;
import au.com.dius.pact.consumer.dsl.PactDslJsonArray;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import com.delivery.webui.client.DispatchCourierClient;
import com.delivery.webui.client.DispatchCourierDashboardClient;
import com.delivery.webui.client.dto.AvailabilityRequest;
import com.delivery.webui.client.dto.DispatchCourierAssignment;
import com.delivery.webui.client.dto.DispatchCourierOffer;
import com.delivery.webui.client.dto.DispatchCourierProfile;
import com.delivery.webui.client.dto.DispatchCourierResponse;
import com.delivery.webui.client.dto.DispatchCourierStatus;
import com.delivery.webui.client.dto.UpdateCourierProfileRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "dispatch-service", pactVersion = PactSpecVersion.V3)
class DispatchServicePactTest {

    private static final Map<String, String> JSON = Map.of("Content-Type", "application/json");

    private static Map<String, String> asUser(String id) {
        return Map.of("X-User-Id", id);
    }

    private static PactDslJsonBody profileBody() {
        return new PactDslJsonBody()
                .integerType("userId", 10L)
                .stringType("displayName", "Andrii Kovalenko")
                .stringType("phone", "+380501234567")
                .stringType("workZone", "Kyiv")
                .stringType("workZoneCode", "UA80000000000093317")
                .decimalType("maxPackageWeight", 15.5);
    }

    @Pact(consumer = "web-ui", provider = "dispatch-service")
    RequestResponsePact courierProfile(PactDslWithProvider builder) {
        PactDslJsonBody update = new PactDslJsonBody()
                .stringType("displayName", "Andrii Kovalenko")
                .stringType("phone", "+380501234567")
                .stringType("workZone", "Kyiv")
                .stringType("workZoneCode", "UA80000000000093317")
                .decimalType("maxPackageWeight", 15.5);
        return builder
                .given("courier 10 exists")
                .uponReceiving("a courier profile read")
                .method("GET").path("/couriers/10")
                .willRespondWith().status(200).headers(JSON).body(profileBody())
                .given("courier 10 exists")
                .uponReceiving("a courier profile update by the courier")
                .method("PATCH").path("/couriers/10")
                .headers(Map.of("X-User-Id", "10", "Content-Type", "application/json"))
                .body(update)
                .willRespondWith().status(200).headers(JSON).body(profileBody())
                .given("courier 10 exists")
                .uponReceiving("a courier profile update by someone else")
                .method("PATCH").path("/couriers/10")
                .headers(Map.of("X-User-Id", "11", "Content-Type", "application/json"))
                .body(update)
                .willRespondWith().status(403)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "courierProfile")
    void readsAndUpdatesCourierProfile(MockServer server) {
        DispatchCourierDashboardClient client = PactClients.create(DispatchCourierDashboardClient.class, server.getUrl());
        UpdateCourierProfileRequest update = new UpdateCourierProfileRequest(
                "Andrii Kovalenko", "+380501234567", "Kyiv", "UA80000000000093317", new BigDecimal("15.5"));

        DispatchCourierProfile profile = client.profile(10);
        assertThat(profile.displayName()).isNotBlank();
        assertThat(profile.maxPackageWeight()).isNotNull();

        assertThat(client.updateProfile(10, 10, update).workZoneCode()).startsWith("UA");
        assertThatThrownBy(() -> client.updateProfile(11, 10, update)).isInstanceOf(HttpClientErrorException.Forbidden.class);
    }

    @Pact(consumer = "web-ui", provider = "dispatch-service")
    RequestResponsePact courierStatusAndAvailability(PactDslWithProvider builder) {
        PactDslJsonBody status = new PactDslJsonBody()
                .integerType("courierId", 10L)
                .booleanType("online", true)
                .stringMatcher("state", "AVAILABLE|BUSY|UNAVAILABLE", "AVAILABLE")
                .integerType("activeAssignments", 0L)
                .nullValue("pendingOfferId");
        return builder
                .given("courier 10 is online")
                .uponReceiving("a courier status read")
                .method("GET").path("/couriers/10/status").headers(asUser("10"))
                .willRespondWith().status(200).headers(JSON).body(status)
                .given("courier 10 is online")
                .uponReceiving("a go-offline request")
                .method("PUT").path("/couriers/10/availability")
                .headers(Map.of("X-User-Id", "10", "Content-Type", "application/json"))
                .body(new PactDslJsonBody().booleanType("online", false))
                .willRespondWith().status(200).headers(JSON)
                .body(new PactDslJsonBody()
                        .integerType("courierId", 10L)
                        .booleanType("online", false)
                        .stringType("state", "UNAVAILABLE")
                        .integerType("activeAssignments", 0L)
                        .nullValue("pendingOfferId"))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "courierStatusAndAvailability")
    void readsStatusAndTogglesAvailability(MockServer server) {
        DispatchCourierDashboardClient client = PactClients.create(DispatchCourierDashboardClient.class, server.getUrl());

        DispatchCourierStatus status = client.status(10, 10);
        assertThat(status.courierId()).isEqualTo(10);
        assertThat(status.state()).isNotBlank();

        client.setAvailability(10, 10, new AvailabilityRequest(false));
    }

    @Pact(consumer = "web-ui", provider = "dispatch-service")
    RequestResponsePact offers(PactDslWithProvider builder) {
        PactDslJsonBody offer = new PactDslJsonBody()
                .integerType("offerId", 55L)
                .integerType("shipmentId", 15L)
                .stringType("status", "PENDING")
                .stringType("expiresAt", "2026-11-22T12:00:00")
                .integerType("secondsLeft", 60L)
                .stringType("pickupAddress", "Kyiv, Khreshchatyk 1")
                .stringType("deliveryAddress", "Lviv, Svobody 10")
                .decimalType("parcelWeight", 2.5)
                .decimalType("distanceKm", 540.0)
                .decimalType("distanceToPickupKm", 1.2)
                .decimalType("price", 885.0);
        PactDslJsonBody assignment = new PactDslJsonBody()
                .integerType("assignmentId", 3L)
                .integerType("shipmentId", 15L)
                .stringType("status", "ACTIVE")
                .booleanType("pickedUp", false)
                .stringType("pickupAddress", "Kyiv, Khreshchatyk 1")
                .stringType("deliveryAddress", "Lviv, Svobody 10");
        return builder
                .given("courier 10 has a pending offer 55")
                .uponReceiving("a current offer read")
                .method("GET").path("/couriers/10/offers/current").headers(asUser("10"))
                .willRespondWith().status(200).headers(JSON).body(offer)
                .given("courier 10 has a pending offer 55")
                .uponReceiving("an offer accept")
                .method("POST").path("/couriers/10/offers/55/accept").headers(asUser("10"))
                .willRespondWith().status(200).headers(JSON).body(assignment)
                .given("courier 10 has a pending offer 55")
                .uponReceiving("an offer decline")
                .method("POST").path("/couriers/10/offers/55/decline").headers(asUser("10"))
                .willRespondWith().status(204)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "offers")
    void handlesOffers(MockServer server) {
        DispatchCourierDashboardClient client = PactClients.create(DispatchCourierDashboardClient.class, server.getUrl());

        DispatchCourierOffer offer = client.currentOffer(10, 10);
        assertThat(offer.offerId()).isEqualTo(55);
        assertThat(offer.price()).isNotNull();

        client.acceptOffer(10, 10, 55);
        client.declineOffer(10, 10, 55);
    }

    @Pact(consumer = "web-ui", provider = "dispatch-service")
    RequestResponsePact noOffer(PactDslWithProvider builder) {
        return builder
                .given("courier 10 has no pending offer")
                .uponReceiving("a current offer read without an offer")
                .method("GET").path("/couriers/10/offers/current").headers(asUser("10"))
                .willRespondWith().status(204)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "noOffer")
    void treatsNoContentAsNoOffer(MockServer server) {
        DispatchCourierDashboardClient client = PactClients.create(DispatchCourierDashboardClient.class, server.getUrl());

        assertThat(client.currentOffer(10, 10)).isNull();
    }

    @Pact(consumer = "web-ui", provider = "dispatch-service")
    RequestResponsePact assignments(PactDslWithProvider builder) {
        DslPart list = PactDslJsonArray.arrayMinLike(1)
                .integerType("assignmentId", 3L)
                .integerType("shipmentId", 15L)
                .stringType("status", "ACTIVE")
                .booleanType("pickedUp", false)
                .stringType("pickupAddress", "Kyiv, Khreshchatyk 1")
                .stringType("deliveryAddress", "Lviv, Svobody 10")
                .closeObject();
        return builder
                .given("courier 10 has active assignment 3")
                .uponReceiving("an active assignments read")
                .method("GET").path("/couriers/10/assignments").query("active=true").headers(asUser("10"))
                .willRespondWith().status(200).headers(JSON).body(list)
                .given("courier 10 has active assignment 3")
                .uponReceiving("an assignment withdraw")
                .method("POST").path("/couriers/shipments/15/withdraw").headers(asUser("10"))
                .willRespondWith().status(204)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "assignments")
    void readsAndWithdrawsAssignments(MockServer server) {
        DispatchCourierDashboardClient client = PactClients.create(DispatchCourierDashboardClient.class, server.getUrl());

        List<DispatchCourierAssignment> active = client.activeAssignments(10, 10);
        assertThat(active).isNotEmpty();
        assertThat(active.getFirst().shipmentId()).isEqualTo(15);

        client.withdrawShipment(10, 15);
    }

    @Pact(consumer = "web-ui", provider = "dispatch-service")
    RequestResponsePact shipmentCourier(PactDslWithProvider builder) {
        return builder
                .given("shipment 15 has courier Andrii")
                .uponReceiving("a current courier lookup for an assigned shipment")
                .method("GET").path("/dispatch/shipments/15/courier")
                .willRespondWith().status(200).headers(JSON)
                .body(new PactDslJsonBody()
                        .integerType("shipmentId", 15L)
                        .booleanType("assigned", true)
                        .stringType("courierName", "Andrii Kovalenko"))
                .given("shipment 16 has no courier")
                .uponReceiving("a current courier lookup for an unassigned shipment")
                .method("GET").path("/dispatch/shipments/16/courier")
                .willRespondWith().status(200).headers(JSON)
                .body(new PactDslJsonBody()
                        .integerType("shipmentId", 16L)
                        .booleanValue("assigned", false)
                        .nullValue("courierName"))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "shipmentCourier")
    void looksUpCurrentCourier(MockServer server) {
        DispatchCourierClient client = PactClients.create(DispatchCourierClient.class, server.getUrl());

        DispatchCourierResponse assigned = client.getShipmentCourier(15);
        assertThat(assigned.assigned()).isTrue();
        assertThat(assigned.courierName()).isNotBlank();

        DispatchCourierResponse unassigned = client.getShipmentCourier(16);
        assertThat(unassigned.assigned()).isFalse();
        assertThat(unassigned.courierName()).isNull();
    }
}
