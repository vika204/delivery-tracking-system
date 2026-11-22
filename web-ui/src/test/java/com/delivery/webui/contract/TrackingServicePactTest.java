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
import com.delivery.webui.client.TrackingLocationClient;
import com.delivery.webui.client.TrackingShipmentClient;
import com.delivery.webui.client.dto.RecordLocationRequest;
import com.delivery.webui.client.dto.TrackingLocationResponse;
import com.delivery.webui.client.dto.TrackingShipmentBatchResponse;
import com.delivery.webui.client.dto.TrackingShipmentResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** web-ui (consumer) -> tracking-service (provider): shipment lookup and location history. */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "tracking-service", pactVersion = PactSpecVersion.V3)
class TrackingServicePactTest {

    private static final Map<String, String> JSON = Map.of("Content-Type", "application/json");
    private static final String POINT_ID = "4b3f1d5e-1c1b-4d6e-9d52-0c1f8f6a7b11";

    @Pact(consumer = "web-ui", provider = "tracking-service")
    RequestResponsePact shipmentLookup(PactDslWithProvider builder) {
        DslPart batch = new PactDslJsonBody()
                .array("shipments").object()
                .integerType("shipmentId", 1L)
                .stringType("deliveryAddress", "Lviv, Svobody 10")
                .closeObject().closeArray()
                .array("missingIds").closeArray();
        return builder
                .given("shipment 1 is known to shipment-service")
                .uponReceiving("a tracking lookup for shipment 1")
                .method("GET").path("/tracking/shipments/1")
                .willRespondWith().status(200).headers(JSON)
                .body(new PactDslJsonBody()
                        .integerType("shipmentId", 1L)
                        .stringType("recipientName", "Marta Shevchenko")
                        .stringType("status", "WAITING_FOR_COURIER"))
                .given("shipment 1 is known to shipment-service")
                .uponReceiving("a tracking batch lookup")
                .method("GET").path("/tracking/shipments/batch").query("ids=1")
                .willRespondWith().status(200).headers(JSON).body(batch)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "shipmentLookup")
    void looksUpShipments(MockServer server) {
        TrackingShipmentClient client = PactClients.create(TrackingShipmentClient.class, server.getUrl());

        TrackingShipmentResponse shipment = client.getShipmentById(1);
        assertThat(shipment.shipmentId()).isEqualTo(1);
        assertThat(shipment.status()).isNotBlank();

        TrackingShipmentBatchResponse batch = client.getShipments(1);
        assertThat(batch.shipments()).isNotEmpty();
        assertThat(batch.shipments().getFirst().deliveryAddress()).isNotBlank();
        assertThat(batch.missingIds()).isEmpty();
    }

    @Pact(consumer = "web-ui", provider = "tracking-service")
    RequestResponsePact missingShipment(PactDslWithProvider builder) {
        return builder
                .given("shipment 404 is unknown to shipment-service")
                .uponReceiving("a tracking lookup for an unknown shipment")
                .method("GET").path("/tracking/shipments/404")
                .willRespondWith().status(404)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "missingShipment")
    void unknownShipmentIsReportedAsNotFound(MockServer server) {
        TrackingShipmentClient client = PactClients.create(TrackingShipmentClient.class, server.getUrl());

        assertThatThrownBy(() -> client.getShipmentById(404)).isInstanceOf(HttpClientErrorException.NotFound.class);
    }

    @Pact(consumer = "web-ui", provider = "tracking-service")
    RequestResponsePact locationHistory(PactDslWithProvider builder) {
        DslPart history = PactDslJsonArray.arrayMinLike(1)
                .integerType("locationId", 1L)
                .integerType("shipmentId", 15L)
                .decimalType("latitude", 50.4501)
                .decimalType("longitude", 30.5234)
                .stringType("recordedAt", "2026-11-12T12:02:00")
                .closeObject();
        return builder
                .given("shipment 15 has recorded locations")
                .uponReceiving("a location history read")
                .method("GET").path("/tracking/shipments/15/locations")
                .query("afterLocationId=0&limit=100")
                .willRespondWith().status(200).headers(JSON).body(history)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "locationHistory")
    void readsLocationHistory(MockServer server) {
        TrackingLocationClient client = PactClients.create(TrackingLocationClient.class, server.getUrl());

        List<TrackingLocationResponse> history = client.getShipmentLocations(15, 0, 100);

        assertThat(history).isNotEmpty();
        assertThat(history.getFirst().latitude()).isNotNull();
        assertThat(history.getFirst().recordedAt()).isNotNull();
    }

    @Pact(consumer = "web-ui", provider = "tracking-service")
    RequestResponsePact recordLocation(PactDslWithProvider builder) {
        return builder
                .given("shipment 15 accepts locations")
                .uponReceiving("a new location point")
                .method("POST").path("/tracking/shipments/15/locations")
                .headers(Map.of("Content-Type", "application/json"))
                .body(new PactDslJsonBody()
                        .stringType("pointId", POINT_ID)
                        .decimalType("latitude", 50.4501)
                        .decimalType("longitude", 30.5234)
                        .stringType("recordedAt", "2026-11-12T12:02:00Z"))
                .willRespondWith().status(201).headers(JSON)
                .body(new PactDslJsonBody()
                        .integerType("locationId", 1L)
                        .integerType("shipmentId", 15L)
                        .decimalType("latitude", 50.4501)
                        .decimalType("longitude", 30.5234)
                        .stringType("recordedAt", "2026-11-12T12:02:00"))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "recordLocation")
    void recordsLocation(MockServer server) {
        TrackingLocationClient client = PactClients.create(TrackingLocationClient.class, server.getUrl());

        ResponseEntity<TrackingLocationResponse> response = client.recordLocation(15, new RecordLocationRequest(
                UUID.fromString(POINT_ID), new BigDecimal("50.4501"), new BigDecimal("30.5234"),
                Instant.parse("2026-11-12T12:02:00Z")));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().locationId()).isNotNull();
    }
}
