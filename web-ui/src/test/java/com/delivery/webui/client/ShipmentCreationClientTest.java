package com.delivery.webui.client;

import com.delivery.webui.client.dto.CreateShipmentRequest;
import com.delivery.webui.client.dto.CreatedShipmentResponse;
import com.delivery.webui.client.dto.ShipmentCreationResult;
import com.delivery.webui.client.dto.ShipmentQuoteRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ShipmentCreationClientTest {

    private static final UUID QUOTE = UUID.fromString("3f2c1d7e-8a4b-4c5d-9e6f-1a2b3c4d5e6f");

    private MockRestServiceServer server;
    private ShipmentCreationClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ShipmentCreationClient(builder.baseUrl("http://shipment.test").build());
    }

    @Test
    void requestsQuoteAsSender() {
        server.expect(requestTo("http://shipment.test/shipments/quotes"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-User-Id", "42"))
                .andExpect(content().json("""
                        {"pickupAddress": "Київ, Khreshchatyk 1", "pickupCityCode": "UA80000000000093317",
                         "deliveryAddress": "Львів, Svobody 10", "deliveryCityCode": "UA46060250010015970",
                         "weight": 2.5, "deliveryType": "EXPRESS"}
                        """))
                .andRespond(withSuccess("""
                        {"quoteId": "%s", "expiresAt": "2026-11-19T12:15:00Z", "distanceKm": 540.2,
                         "totalPrice": 1340.70, "breakdown": {"total": 1340.70}}
                        """.formatted(QUOTE), MediaType.APPLICATION_JSON));

        var quote = client.quote(42, new ShipmentQuoteRequest("Київ, Khreshchatyk 1", "UA80000000000093317",
                "Львів, Svobody 10", "UA46060250010015970", new BigDecimal("2.5"), "EXPRESS"));

        assertThat(quote.quoteId()).isEqualTo(QUOTE);
        assertThat(quote.totalPrice()).isEqualByComparingTo("1340.70");
        server.verify();
    }

    @Test
    void passesIdempotencyKeyAndReportsReplay() {
        HttpHeaders replayed = new HttpHeaders();
        replayed.add("Idempotent-Replayed", "true");
        server.expect(requestTo("http://shipment.test/shipments"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-User-Id", "42"))
                .andExpect(header("Idempotency-Key", "key-1"))
                .andExpect(content().json("{\"quoteId\": \"%s\", \"senderName\": \"Olena\"}".formatted(QUOTE)))
                .andRespond(withSuccess("""
                        {"shipmentId": 15, "status": "WAITING_FOR_COURIER", "price": 120.50, "userId": 42}
                        """, MediaType.APPLICATION_JSON).headers(replayed));

        ShipmentCreationResult creation = client.create(42, "key-1", request());

        assertThat(creation.shipment()).isEqualTo(new CreatedShipmentResponse(15, "WAITING_FOR_COURIER", new BigDecimal("120.50")));
        assertThat(creation.replayed()).isTrue();
    }

    @Test
    void firstCreationIsNotReplay() {
        server.expect(requestTo("http://shipment.test/shipments"))
                .andRespond(withSuccess("{\"shipmentId\": 16}", MediaType.APPLICATION_JSON));

        assertThat(client.create(42, "key-2", request()).replayed()).isFalse();
    }

    private static CreateShipmentRequest request() {
        return new CreateShipmentRequest(QUOTE, "Olena", "+380501234567", "Marta", "+380671234567",
                new BigDecimal("30"), new BigDecimal("20"), new BigDecimal("15"));
    }
}
