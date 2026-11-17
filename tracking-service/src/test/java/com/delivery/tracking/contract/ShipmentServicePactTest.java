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
import com.delivery.tracking.client.dto.ShipmentDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "shipment-service", pactVersion = PactSpecVersion.V3)
class ShipmentServicePactTest {

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
                .headers(Map.of("Content-Type", "application/json"))
                .body(body)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "getShipmentById")
    void getShipmentById_returnsRequiredFields(MockServer mockServer) {
        RestClient restClient = RestClient.builder().baseUrl(mockServer.getUrl()).build();
        ShipmentClient client = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(ShipmentClient.class);

        ShipmentDto shipment = client.getShipmentById(1L);

        assertThat(shipment.shipmentId()).isNotNull();
        assertThat(shipment.recipientName()).isNotBlank();
        assertThat(shipment.status()).isNotBlank();
    }
}
