package com.delivery.webui.client;

import com.delivery.webui.client.dto.TrackingLocationResponse;
import com.delivery.webui.client.dto.RecordLocationRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class TrackingLocationClientTest {

    private MockRestServiceServer server;
    private TrackingLocationClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer
                .bindTo(builder)
                .build();
        client = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(builder.baseUrl("http://tracking.test").build()))
                .build().createClient(TrackingLocationClient.class);
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    @Test
    void formsPagedRequestAndDeserializesCoordinates() {
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
    }

    @ParameterizedTest
    @EnumSource(value = HttpStatus.class, names = {"CREATED", "OK"})
    void preservesGpsWriteBodyAndNewOrReplayedStatus(HttpStatus status) {
        UUID pointId = UUID.fromString("8c7b2f9e-1d3a-4c5b-9e8f-7a6b5c4d3e2f");
        server.expect(requestTo("http://tracking.test/tracking/shipments/15/locations"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"pointId":"8c7b2f9e-1d3a-4c5b-9e8f-7a6b5c4d3e2f", "latitude":50.45,
                         "longitude":30.52, "recordedAt":"2026-11-19T10:00:00Z"}
                        """))
                .andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON).body("""
                        {"locationId":101, "shipmentId":15, "latitude":50.45,
                         "longitude":30.52, "recordedAt":"2026-11-19T10:00:00"}
                        """));

        var response = client.recordLocation(15, new RecordLocationRequest(pointId,
                new BigDecimal("50.45"), new BigDecimal("30.52"), Instant.parse("2026-11-19T10:00:00Z")));

        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).isEqualTo(new TrackingLocationResponse(101L, 15L,
                new BigDecimal("50.45"), new BigDecimal("30.52"), LocalDateTime.of(2026, 11, 19, 10, 0)));
    }
}
