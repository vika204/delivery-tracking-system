package com.delivery.webui.client;

import com.delivery.webui.client.dto.TrackingLocationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TrackingLocationClientTest {

    @Test
    void formsPagedRequestAndDeserializesCoordinates() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer
                .bindTo(builder)
                .build();
        TrackingLocationClient client = new TrackingLocationClient(
                builder.baseUrl("http://tracking.test").build()
        );

        server.expect(once(), requestTo(
                        "http://tracking.test/tracking/shipments/15/locations"
                                + "?afterLocationId=42&limit=100"
                ))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [
                          {
                            "locationId": 43,
                            "shipmentId": 15,
                            "latitude": 50.450100,
                            "longitude": 30.523400,
                            "recordedAt": "2026-11-12T09:30:00"
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        List<TrackingLocationResponse> result =
                client.getShipmentLocations(15, 42, 100);

        assertThat(result).containsExactly(new TrackingLocationResponse(
                43L,
                15L,
                new BigDecimal("50.450100"),
                new BigDecimal("30.523400"),
                LocalDateTime.of(2026, 11, 12, 9, 30)
        ));
        server.verify();
    }
}
