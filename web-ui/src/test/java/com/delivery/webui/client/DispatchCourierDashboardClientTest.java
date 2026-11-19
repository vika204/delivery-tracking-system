package com.delivery.webui.client;

import com.delivery.webui.client.dto.DispatchCourierAssignment;
import com.delivery.webui.client.dto.DispatchCourierStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DispatchCourierDashboardClientTest {

    private MockRestServiceServer server;
    private DispatchCourierDashboardClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new DispatchCourierDashboardClient(builder.baseUrl("http://dispatch.test").build());
    }

    @Test
    void readsStatusAsCourier() {
        server.expect(requestTo("http://dispatch.test/couriers/7/status"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-User-Id", "7"))
                .andRespond(withSuccess("""
                        {"courierId": 7, "online": true, "state": "BUSY", "activeAssignments": 1, "pendingOfferId": null}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.status(7)).isEqualTo(new DispatchCourierStatus(7, true, "BUSY", 1, null));
        server.verify();
    }

    @Test
    void returnsNoOfferWhenDispatchRespondsNoContent() {
        server.expect(requestTo("http://dispatch.test/couriers/7/offers/current"))
                .andRespond(withNoContent());

        assertThat(client.currentOffer(7)).isNull();
    }

    @Test
    void readsActiveAssignmentsIgnoringExtraFields() {
        server.expect(requestTo("http://dispatch.test/couriers/7/assignments?active=true"))
                .andExpect(header("X-User-Id", "7"))
                .andRespond(withSuccess("""
                        [{"assignmentId": 3, "shipmentId": 15, "status": "ACTIVE", "pickedUp": false,
                          "pickupAddress": "Kyiv, A", "deliveryAddress": "Kyiv, B", "price": 120.5}]
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.activeAssignments(7))
                .containsExactly(new DispatchCourierAssignment(3, 15, "ACTIVE", false, "Kyiv, A", "Kyiv, B"));
    }

    @Test
    void switchesAvailability() {
        server.expect(requestTo("http://dispatch.test/couriers/7/availability"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("X-User-Id", "7"))
                .andExpect(content().json("{\"online\": false}"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.setAvailability(7, false);
        server.verify();
    }

    @Test
    void acceptsAndDeclinesOffers() {
        server.expect(requestTo("http://dispatch.test/couriers/7/offers/55/accept"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-User-Id", "7"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://dispatch.test/couriers/7/offers/56/decline"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withNoContent());

        client.acceptOffer(7, 55);
        client.declineOffer(7, 56);
        server.verify();
    }

    @Test
    void propagatesRejectedAccept() {
        server.expect(requestTo("http://dispatch.test/couriers/7/offers/55/accept"))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> client.acceptOffer(7, 55)).isInstanceOf(HttpClientErrorException.Conflict.class);
    }
}
