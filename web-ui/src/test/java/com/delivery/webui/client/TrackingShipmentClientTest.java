package com.delivery.webui.client;

import com.delivery.webui.client.dto.ShipmentDetailsResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TrackingShipmentClientTest {

    @Test
    void requestsExistingBatchContractAndReadsDestination() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer
                .bindTo(builder)
                .build();
        TrackingShipmentClient client = new TrackingShipmentClient(
                builder.baseUrl("http://tracking.test").build()
        );

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

        ShipmentDetailsResponse result = client.getShipmentDetails(15);

        assertThat(result).isEqualTo(new ShipmentDetailsResponse(
                15L,
                "Kyiv, Delivery Street 2"
        ));
        server.verify();
    }
}
