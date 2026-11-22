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
import com.delivery.webui.client.ClientShipmentClient;
import com.delivery.webui.client.CourierShipmentClient;
import com.delivery.webui.client.ShipmentCreationClient;
import com.delivery.webui.client.dto.ClientShipmentResponse;
import com.delivery.webui.client.dto.CourierShipmentResponse;
import com.delivery.webui.client.dto.CreateShipmentRequest;
import com.delivery.webui.client.dto.CreatedShipmentResponse;
import com.delivery.webui.client.dto.ShipmentQuoteRequest;
import com.delivery.webui.client.dto.ShipmentQuoteResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * web-ui (consumer) -> shipment-service (provider): the sender's parcels, the courier's view and actions, and
 * quote -> create. Every interaction uses shipment id 1 because each provider state seeds a fresh database.
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "shipment-service", pactVersion = PactSpecVersion.V3)
class ShipmentServicePactTest {

    private static final Map<String, String> JSON = Map.of("Content-Type", "application/json");
    private static final String QUOTE_ID = "7d444840-9dc0-11d1-b245-5ffdce74fad2";

    private static Map<String, String> asUser(String id) {
        return Map.of("X-User-Id", id);
    }

    private static PactDslJsonBody senderShipment(String status) {
        return new PactDslJsonBody()
                .integerType("shipmentId", 1L)
                .integerType("userId", 42L)
                .stringType("status", status)
                .stringType("pickupAddress", "Kyiv, Khreshchatyk 1")
                .stringType("deliveryAddress", "Lviv, Svobody 10")
                .stringType("recipientName", "Marta Shevchenko")
                .stringType("recipientPhone", "+380671234567")
                .stringType("createdAt", "2026-11-12T12:02:00")
                .decimalType("distance", 540.0)
                .decimalType("weight", 2.5)
                .decimalType("length", 30.0)
                .decimalType("width", 20.0)
                .decimalType("height", 15.0)
                .decimalType("price", 885.0)
                .nullValue("assignedCourierId")
                .nullValue("assignedCourierName");
    }

    @Pact(consumer = "web-ui", provider = "shipment-service")
    RequestResponsePact senderReads(PactDslWithProvider builder) {
        DslPart mine = PactDslJsonArray.arrayMinLike(1)
                .integerType("shipmentId", 1L)
                .integerType("userId", 42L)
                .stringType("status", "WAITING_FOR_COURIER")
                .stringType("pickupAddress", "Kyiv, Khreshchatyk 1")
                .stringType("deliveryAddress", "Lviv, Svobody 10")
                .stringType("recipientName", "Marta Shevchenko")
                .stringType("createdAt", "2026-11-12T12:02:00")
                .decimalType("price", 885.0)
                .closeObject();
        return builder
                .given("user 42 has waiting shipment 1")
                .uponReceiving("the sender's parcel list")
                .method("GET").path("/shipments/mine").query("page=0&size=50").headers(asUser("42"))
                .willRespondWith().status(200).headers(JSON).body(mine)
                .given("user 42 has waiting shipment 1")
                .uponReceiving("a parcel read")
                .method("GET").path("/shipments/1")
                .willRespondWith().status(200).headers(JSON).body(senderShipment("WAITING_FOR_COURIER"))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "senderReads")
    void sendersReadTheirParcels(MockServer server) {
        ClientShipmentClient client = PactClients.create(ClientShipmentClient.class, server.getUrl());

        List<ClientShipmentResponse> mine = client.getMyShipments(42, 0, 50);
        assertThat(mine).isNotEmpty();
        assertThat(mine.getFirst().status()).isNotBlank();

        ClientShipmentResponse parcel = client.getShipment(1);
        assertThat(parcel.userId()).isEqualTo(42);
        assertThat(parcel.price()).isNotNull();
        assertThat(parcel.assignedCourierId()).isNull();
    }

    @Pact(consumer = "web-ui", provider = "shipment-service")
    RequestResponsePact senderCancels(PactDslWithProvider builder) {
        return builder
                .given("user 42 has waiting shipment 1")
                .uponReceiving("a cancel by the sender")
                .method("POST").path("/shipments/1/cancel").headers(asUser("42"))
                .willRespondWith().status(200).headers(JSON)
                .body(new PactDslJsonBody().integerType("shipmentId", 1L).stringType("status", "CANCELLED"))
                .given("user 42 has waiting shipment 1")
                .uponReceiving("a cancel by someone who is not the sender")
                .method("POST").path("/shipments/1/cancel").headers(asUser("99"))
                .willRespondWith().status(403)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "senderCancels")
    void senderCancelsAndOthersAreRefused(MockServer server) {
        ClientShipmentClient client = PactClients.create(ClientShipmentClient.class, server.getUrl());

        client.cancelShipment(42, 1);
        assertThatThrownBy(() -> client.cancelShipment(99, 1)).isInstanceOf(HttpClientErrorException.Forbidden.class);
    }

    @Pact(consumer = "web-ui", provider = "shipment-service")
    RequestResponsePact unknownParcel(PactDslWithProvider builder) {
        return builder
                .uponReceiving("a read of an unknown parcel")
                .method("GET").path("/shipments/404")
                .willRespondWith().status(404)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "unknownParcel")
    void unknownParcelIsNotFound(MockServer server) {
        ClientShipmentClient client = PactClients.create(ClientShipmentClient.class, server.getUrl());

        assertThatThrownBy(() -> client.getShipment(404)).isInstanceOf(HttpClientErrorException.NotFound.class);
    }

    @Pact(consumer = "web-ui", provider = "shipment-service")
    RequestResponsePact courierView(PactDslWithProvider builder) {
        PactDslJsonBody view = new PactDslJsonBody()
                .integerType("shipmentId", 1L)
                .stringType("status", "ASSIGNED")
                .stringType("pickupAddress", "Kyiv, Khreshchatyk 1")
                .stringType("deliveryAddress", "Lviv, Svobody 10")
                .object("recipient").stringType("initials", "M. S.").stringType("phone", "+380671234567").closeObject().asBody()
                .decimalType("weight", 2.5)
                .decimalType("length", 30.0)
                .decimalType("width", 20.0)
                .decimalType("height", 15.0)
                .decimalType("distance", 540.0)
                .decimalType("price", 885.0);
        return builder
                .given("shipment 1 is assigned to courier 7")
                .uponReceiving("the assigned courier's view")
                .method("GET").path("/shipments/1/courier-view").headers(asUser("7"))
                .willRespondWith().status(200).headers(JSON).body(view)
                .given("shipment 1 is assigned to courier 7")
                .uponReceiving("the courier view requested by a different courier")
                .method("GET").path("/shipments/1/courier-view").headers(asUser("8"))
                .willRespondWith().status(403)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "courierView")
    void onlyTheAssignedCourierSeesTheParcel(MockServer server) {
        CourierShipmentClient client = PactClients.create(CourierShipmentClient.class, server.getUrl());

        CourierShipmentResponse view = client.courierView(7, 1);
        assertThat(view.status()).isEqualTo("ASSIGNED");
        assertThat(view.recipient().initials()).isNotBlank();
        assertThat(view.price()).isNotNull();

        assertThatThrownBy(() -> client.courierView(8, 1)).isInstanceOf(HttpClientErrorException.Forbidden.class);
    }

    @Pact(consumer = "web-ui", provider = "shipment-service")
    RequestResponsePact courierPickup(PactDslWithProvider builder) {
        return builder
                .given("shipment 1 is assigned to courier 7")
                .uponReceiving("a pickup confirmation by the assigned courier")
                .method("POST").path("/shipments/1/pickup").headers(asUser("7"))
                .willRespondWith().status(200).headers(JSON)
                .body(new PactDslJsonBody().integerType("shipmentId", 1L).stringType("status", "IN_TRANSIT"))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "courierPickup")
    void courierConfirmsPickup(MockServer server) {
        PactClients.create(CourierShipmentClient.class, server.getUrl()).confirmPickup(7, 1);
    }

    @Pact(consumer = "web-ui", provider = "shipment-service")
    RequestResponsePact courierDelivery(PactDslWithProvider builder) {
        return builder
                .given("shipment 1 is in transit with courier 7")
                .uponReceiving("a delivery completion by the assigned courier")
                .method("POST").path("/shipments/1/deliver").headers(asUser("7"))
                .willRespondWith().status(200).headers(JSON)
                .body(new PactDslJsonBody().integerType("shipmentId", 1L).stringType("status", "DELIVERED"))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "courierDelivery")
    void courierCompletesDelivery(MockServer server) {
        PactClients.create(CourierShipmentClient.class, server.getUrl()).completeDelivery(7, 1);
    }

    @Pact(consumer = "web-ui", provider = "shipment-service")
    RequestResponsePact quoteAndCreate(PactDslWithProvider builder) {
        PactDslJsonBody quoteRequest = new PactDslJsonBody()
                .stringType("pickupAddress", "Khreshchatyk 1")
                .stringMatcher("pickupCityCode", "UA\\d{17}", "UA80000000000093317")
                .stringType("deliveryAddress", "Svobody 10")
                .stringMatcher("deliveryCityCode", "UA\\d{17}", "UA46060250010015970")
                .decimalType("weight", 2.5);
        PactDslJsonBody quote = new PactDslJsonBody()
                .uuid("quoteId")
                .stringType("expiresAt", "2026-11-22T12:00:00Z")
                .decimalType("distanceKm", 540.0)
                .decimalType("totalPrice", 885.0);
        PactDslJsonBody createRequest = new PactDslJsonBody()
                .stringType("quoteId", QUOTE_ID)
                .stringType("senderName", "Olena Kovalenko")
                .stringType("senderPhone", "+380501234567")
                .stringType("recipientName", "Marta Shevchenko")
                .stringType("recipientPhone", "+380671234567")
                .decimalType("length", 30.0)
                .decimalType("width", 20.0)
                .decimalType("height", 15.0);
        PactDslJsonBody created = new PactDslJsonBody()
                .integerType("shipmentId", 1L)
                .stringType("status", "WAITING_FOR_COURIER")
                .decimalType("price", 885.0);
        return builder
                .given("routing can geocode Kyiv and Lviv")
                .uponReceiving("a price quote request")
                .method("POST").path("/shipments/quotes")
                .headers(Map.of("X-User-Id", "42", "Content-Type", "application/json"))
                .body(quoteRequest)
                .willRespondWith().status(201).headers(JSON).body(quote)
                .given("a usable price quote exists for user 42")
                .uponReceiving("a shipment creation from that quote")
                .method("POST").path("/shipments")
                .headers(Map.of("X-User-Id", "42", "Idempotency-Key", "key-1", "Content-Type", "application/json"))
                .body(createRequest)
                .willRespondWith().status(200).headers(JSON).body(created)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "quoteAndCreate")
    void quotesThenCreates(MockServer server) {
        ShipmentCreationClient client = PactClients.create(ShipmentCreationClient.class, server.getUrl());

        ShipmentQuoteResponse quote = client.quote(42, new ShipmentQuoteRequest(
                "Khreshchatyk 1", "UA80000000000093317", "Svobody 10", "UA46060250010015970", new BigDecimal("2.5")));
        assertThat(quote.quoteId()).isNotNull();
        assertThat(quote.totalPrice()).isNotNull();

        ResponseEntity<CreatedShipmentResponse> created = client.create(42, "key-1", new CreateShipmentRequest(
                UUID.fromString(QUOTE_ID), "Olena Kovalenko", "+380501234567",
                "Marta Shevchenko", "+380671234567",
                new BigDecimal("30.0"), new BigDecimal("20.0"), new BigDecimal("15.0")));
        assertThat(created.getBody()).isNotNull();
        assertThat(created.getBody().status()).isNotBlank();
    }

    @Pact(consumer = "web-ui", provider = "shipment-service")
    RequestResponsePact quoteRejected(PactDslWithProvider builder) {
        return builder
                .given("routing can geocode Kyiv and Lviv")
                .uponReceiving("a price quote request with an unknown settlement")
                .method("POST").path("/shipments/quotes")
                .headers(Map.of("X-User-Id", "42", "Content-Type", "application/json"))
                .body(new PactDslJsonBody()
                        .stringType("pickupAddress", "Khreshchatyk 1")
                        .stringValue("pickupCityCode", "UA80000000000093317")
                        .stringType("deliveryAddress", "Nowhere 1")
                        .stringValue("deliveryCityCode", "UA99000000000000000")
                        .decimalType("weight", 2.5))
                .willRespondWith().status(422)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "quoteRejected")
    void unknownSettlementIsUnprocessable(MockServer server) {
        ShipmentCreationClient client = PactClients.create(ShipmentCreationClient.class, server.getUrl());

        assertThatThrownBy(() -> client.quote(42, new ShipmentQuoteRequest(
                "Khreshchatyk 1", "UA80000000000093317", "Nowhere 1", "UA99000000000000000", new BigDecimal("2.5"))))
                .isInstanceOf(HttpClientErrorException.UnprocessableContent.class);
    }
}
