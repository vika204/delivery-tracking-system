package com.delivery.webui.client;

import com.delivery.webui.client.dto.ClientShipmentResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ShipmentReadClientTest {

    private static final String SHIPMENT = """
            {"shipmentId":515,"userId":42,"status":"WAITING_FOR_COURIER",
             "pickupAddress":"Київ, Іллінська, 2","deliveryAddress":"Київ, Контрактова площа, 2",
             "recipientName":"Test Recipient","recipientPhone":"+380501234569",
             "createdAt":"2026-11-19T12:34:56","distance":0.02,"weight":1,
             "length":20,"width":15,"height":10,"price":60.03,
             "assignedCourierId":null,"assignedCourierName":null,
             "senderName":"Ignored extra backend field","quoteId":"00000000-0000-0000-0000-000000000001"}
            """;

    private MockRestServiceServer server;
    private ShipmentReadClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://shipment.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(builder.build()))
                .build().createClient(ShipmentReadClient.class);
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    @Test
    void readsMineUsingClientHeaderAndBoundedPagination() {
        server.expect(requestTo("http://shipment.test/shipments/mine?page=0&size=20"))
                .andExpect(method(HttpMethod.GET)).andExpect(header("X-User-Id", "42"))
                .andRespond(withSuccess("[" + SHIPMENT + "]", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://shipment.test/shipments/mine?page=1&size=20"))
                .andExpect(header("X-User-Id", "42"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        List<ClientShipmentResponse> page = client.getMyShipments(42, 0, 20);

        assertThat(page).hasSize(1);
        ClientShipmentResponse shipment = page.getFirst();
        assertThat(shipment.shipmentId()).isEqualTo(515);
        assertThat(shipment.userId()).isEqualTo(42);
        assertThat(shipment.status()).isEqualTo("WAITING_FOR_COURIER");
        assertThat(shipment.deliveryAddress()).isEqualTo("Київ, Контрактова площа, 2");
        assertThat(shipment.distance()).isEqualByComparingTo("0.02");
        assertThat(shipment.price()).isEqualByComparingTo("60.03");
        assertThat(shipment.createdAt()).isEqualTo(LocalDateTime.of(2026, 11, 19, 12, 34, 56));
        assertThat(client.getMyShipments(42, 1, 20)).isEmpty();
    }

    @Test
    void readsOnlyTheRequestedShipmentDetails() {
        server.expect(requestTo("http://shipment.test/shipments/515"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(SHIPMENT, MediaType.APPLICATION_JSON));

        assertThat(client.getShipment(515).shipmentId()).isEqualTo(515);
    }

    @Test
    void backendFailureIsNotReplacedWithAnEmptyList() {
        server.expect(requestTo("http://shipment.test/shipments/mine?page=0&size=20"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.getMyShipments(42, 0, 20))
                .isInstanceOf(HttpServerErrorException.ServiceUnavailable.class);
    }
}
