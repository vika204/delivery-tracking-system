package com.delivery.webui.client;

import com.delivery.webui.client.dto.DispatchCourierResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DispatchCourierClientTest {

    @Test
    void requestsCourierForSpecificShipment() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer
                .bindTo(builder)
                .build();
        DispatchCourierClient client = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(builder.baseUrl("http://dispatch.test").build()))
                .build().createClient(DispatchCourierClient.class);

        server.expect(once(), requestTo(
                        "http://dispatch.test/dispatch/shipments/15/courier"
                ))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "shipmentId": 15,
                          "assigned": true,
                          "courierName": "Andrii Kovalenko"
                        }
                        """, MediaType.APPLICATION_JSON));

        DispatchCourierResponse result =
                client.getShipmentCourier(15L);

        assertThat(result).isEqualTo(new DispatchCourierResponse(
                15L,
                true,
                "Andrii Kovalenko"
        ));
        server.verify();
    }

}
