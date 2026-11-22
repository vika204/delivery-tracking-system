package com.delivery.tracking.contract;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import com.delivery.tracking.client.ShipmentClient;
import com.delivery.tracking.client.dto.CreateShipmentRequest;
import com.delivery.tracking.client.dto.ShipmentBatchResponse;
import com.delivery.tracking.client.dto.ShipmentDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** tracking-service (consumer) -> shipment-service (provider): single read, batch read, create, unknown shipment. */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "shipment-service", pactVersion = PactSpecVersion.V3)
class ShipmentServicePactTest {

    private static final Map<String, String> JSON = Map.of("Content-Type", "application/json");
    private static final String QUOTE_ID = "7d444840-9dc0-11d1-b245-5ffdce74fad2";

    private static ShipmentClient client(MockServer mockServer) {
        RestClient restClient = RestClient.builder().baseUrl(mockServer.getUrl()).build();
        return HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(ShipmentClient.class);
    }

    @Pact(consumer = "tracking-service", provider = "shipment-service")
    RequestResponsePact getShipmentById(PactDslWithProvider builder) {
        PactDslJsonBody body = new PactDslJsonBody()
                .integerType("shipmentId", 1L)
                .stringType("recipientName", "Ivan Petrenko")
                .stringType("status", "CREATED");

        return builder
                .given("shipment 1 exists")
                .uponReceiving("a request for shipment 1")
                .method("GET")
                .path("/shipments/1")
                .willRespondWith()
                .status(200)
                .headers(JSON)
                .body(body)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "getShipmentById")
    void getShipmentById_returnsRequiredFields(MockServer mockServer) {
        ShipmentDto shipment = client(mockServer).getShipmentById(1L);

        assertThat(shipment.shipmentId()).isNotNull();
        assertThat(shipment.recipientName()).isNotBlank();
        assertThat(shipment.status()).isNotBlank();
    }

    @Pact(consumer = "tracking-service", provider = "shipment-service")
    RequestResponsePact unknownShipment(PactDslWithProvider builder) {
        return builder
                .given("shipment 404 does not exist")
                .uponReceiving("a request for an unknown shipment")
                .method("GET")
                .path("/shipments/404")
                .willRespondWith()
                .status(404)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "unknownShipment")
    void unknownShipment_isReportedAsNotFound(MockServer mockServer) {
        assertThatThrownBy(() -> client(mockServer).getShipmentById(404L))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
    }

    @Pact(consumer = "tracking-service", provider = "shipment-service")
    RequestResponsePact getShipmentsBatch(PactDslWithProvider builder) {
        PactDslJsonBody body = new PactDslJsonBody()
                .minArrayLike("shipments", 1)
                .integerType("shipmentId", 1L)
                .stringType("status", "WAITING_FOR_COURIER")
                .stringType("pickupAddress", "Kyiv, Khreshchatyk 1")
                .stringType("deliveryAddress", "Lviv, Svobody 10")
                .closeObject().closeArray()
                .array("missingIds").numberValue(404L).closeArray().asBody();

        return builder
                .given("shipment 1 exists")
                .uponReceiving("a batch request for shipments 1 and 404")
                .method("GET")
                .path("/shipments/batch")
                .query("ids=1&ids=404")
                .willRespondWith()
                .status(200)
                .headers(JSON)
                .body(body)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "getShipmentsBatch")
    void getShipmentsBatch_returnsFoundAndMissing(MockServer mockServer) {
        ShipmentBatchResponse batch = client(mockServer).getShipmentsBatch(List.of(1L, 404L));

        assertThat(batch.shipments()).hasSize(1);
        assertThat(batch.shipments().getFirst().deliveryAddress()).isNotBlank();
        assertThat(batch.missingIds()).containsExactly(404L);
    }

    @Pact(consumer = "tracking-service", provider = "shipment-service")
    RequestResponsePact createShipment(PactDslWithProvider builder) {
        PactDslJsonBody request = new PactDslJsonBody()
                .stringType("quoteId", QUOTE_ID)
                .stringType("senderName", "Olena Kovalenko")
                .stringType("senderPhone", "+380501234567")
                .stringType("recipientName", "Marta Shevchenko")
                .stringType("recipientPhone", "+380671234567")
                .decimalType("length", 30.0)
                .decimalType("width", 20.0)
                .decimalType("height", 15.0);
        PactDslJsonBody response = new PactDslJsonBody()
                .integerType("shipmentId", 1L)
                .stringType("recipientName", "Marta Shevchenko")
                .stringType("status", "WAITING_FOR_COURIER");

        return builder
                .given("a usable price quote exists for user 42")
                .uponReceiving("a shipment creation from a quote")
                .method("POST")
                .path("/shipments")
                .headers(Map.of("Idempotency-Key", "key-1", "X-User-Id", "42", "Content-Type", "application/json"))
                .body(request)
                .willRespondWith()
                .status(200)
                .headers(JSON)
                .body(response)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "createShipment")
    void createShipment_sendsIdempotencyKeyAndUser(MockServer mockServer) {
        ShipmentDto created = client(mockServer).createShipment("key-1", 42L, new CreateShipmentRequest(
                UUID.fromString(QUOTE_ID), "Olena Kovalenko", "+380501234567",
                "Marta Shevchenko", "+380671234567",
                new BigDecimal("30.0"), new BigDecimal("20.0"), new BigDecimal("15.0")));

        assertThat(created.shipmentId()).isNotNull();
        assertThat(created.status()).isNotBlank();
    }
}
