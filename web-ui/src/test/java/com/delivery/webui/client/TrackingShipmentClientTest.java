package com.delivery.webui.client;

import com.delivery.webui.client.dto.ShipmentDetailsResponse;
import com.delivery.webui.client.dto.TrackingShipmentBatchResponse;
import com.delivery.webui.client.dto.TrackingShipmentResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class TrackingShipmentClientTest {

    private MockRestServiceServer server;
    private TrackingShipmentClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer
                .bindTo(builder)
                .build();
        client = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(builder.baseUrl("http://tracking.test").build()))
                .build().createClient(TrackingShipmentClient.class);
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    @Test
    void requestsExistingBatchContractAndReadsDestination() {
        server.expect(once(), requestTo(
                        "http://tracking.test/tracking/shipments/batch?ids=15"
                ))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "shipments": [
                            {
                              "shipmentId": 15,
                              "status": "IN_TRANSIT",
                              "pickupAddress": "Private pickup",
                              "deliveryAddress": "Kyiv, Delivery Street 2"
                            }
                          ],
                          "missingIds": []
                        }
                        """, MediaType.APPLICATION_JSON));

        TrackingShipmentBatchResponse result = client.getShipments(15);

        assertThat(result.shipments()).containsExactly(new ShipmentDetailsResponse(
                15L,
                "Kyiv, Delivery Street 2"
        ));
    }

    @Test
    void readsRealShipmentStatus() {
        server.expect(requestTo("http://tracking.test/tracking/shipments/15"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"shipmentId\": 15, \"status\": \"DELIVERED\"}", MediaType.APPLICATION_JSON));

        assertThat(client.getShipmentById(15)).isEqualTo(new TrackingShipmentResponse(15L, null, "DELIVERED"));
    }

    @Test
    void propagatesUnavailableServiceWithoutInventingStatus() {
        server.expect(requestTo("http://tracking.test/tracking/shipments/15"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.getShipmentById(15))
                .isInstanceOf(HttpServerErrorException.ServiceUnavailable.class);
    }
}
