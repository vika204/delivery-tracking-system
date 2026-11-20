package com.delivery.webui.controller;

import com.delivery.webui.controller.support.LoopbackInterceptor;
import com.delivery.webui.config.ShipmentCreationProperties;
import com.delivery.webui.client.ShipmentCreationClient;
import com.delivery.webui.exception.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/** BFF разом зі справжнім клієнтом; shipment-service імітує MockRestServiceServer. */
class ShipmentCreationApiControllerTest {

    private static final long SENDER = 42;
    private static final String QUOTE = "3f2c1d7e-8a4b-4c5d-9e6f-1a2b3c4d5e6f";
    private static final String CREATE_BODY = """
            {"quoteId": "%s", "senderName": "Olena", "senderPhone": "+380501234567",
             "recipientName": "Marta", "recipientPhone": "+380671234567", "length": 30, "width": 20, "height": 15}
            """.formatted(QUOTE);

    private MockRestServiceServer shipmentService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        shipmentService = MockRestServiceServer.bindTo(builder).build();
        ShipmentCreationClient client = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(builder.baseUrl("http://shipment.test").build()))
                .build().createClient(ShipmentCreationClient.class);
        mockMvc = standaloneSetup(new ShipmentCreationApiController(client, new ShipmentCreationProperties(SENDER)))
                .setControllerAdvice(new ApiExceptionHandler()).addInterceptors(new LoopbackInterceptor())
                .build();
    }

    @Test
    void returnsQuoteCalculatedByShipmentService() throws Exception {
        shipmentService.expect(requestTo("http://shipment.test/shipments/quotes"))
                .andExpect(header("X-User-Id", "42"))
                .andRespond(withSuccess("""
                        {"quoteId": "%s", "expiresAt": "2026-11-19T12:15:00Z", "distanceKm": 7.3, "totalPrice": 142.00}
                        """.formatted(QUOTE), MediaType.APPLICATION_JSON));

        mockMvc.perform(local(post("/api/shipments/quotes")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"pickupAddress": "Київ, A", "pickupCityCode": "UA80000000000093317",
                         "deliveryAddress": "Київ, B", "deliveryCityCode": "UA80000000000093317", "weight": 4.2}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quoteId").value(QUOTE))
                .andExpect(jsonPath("$.totalPrice").value(142.0));
    }

    @Test
    void createsShipmentWithClientIdempotencyKey() throws Exception {
        shipmentService.expect(requestTo("http://shipment.test/shipments"))
                .andExpect(header("Idempotency-Key", "key-1"))
                .andExpect(header("X-User-Id", "42"))
                .andRespond(withSuccess("{\"shipmentId\": 15, \"status\": \"WAITING_FOR_COURIER\", \"price\": 142}",
                        MediaType.APPLICATION_JSON));

        mockMvc.perform(create("key-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipmentId").value(15))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .doesNotExist("Idempotent-Replayed"));
        shipmentService.verify();
    }

    @Test
    void marksReplayedCreation() throws Exception {
        HttpHeaders replayed = new HttpHeaders();
        replayed.add("Idempotent-Replayed", "true");
        shipmentService.expect(requestTo("http://shipment.test/shipments"))
                .andRespond(withSuccess("{\"shipmentId\": 15}", MediaType.APPLICATION_JSON).headers(replayed));

        mockMvc.perform(create("key-1"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Idempotent-Replayed", "true"));
    }

    @Test
    void passesQuoteProblemTypeSoPageCanRecalculate() throws Exception {
        shipmentService.expect(requestTo("http://shipment.test/shipments"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {"type": "urn:delivery:problem:quote-expired", "status": 422,
                                 "detail": "Price quote %s has expired, request a new one"}
                                """.formatted(QUOTE)));

        mockMvc.perform(create("key-1"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("urn:delivery:problem:quote-expired"))
                .andExpect(jsonPath("$.detail").value("Price quote " + QUOTE + " has expired, request a new one"));
    }

    @Test
    void passesFieldErrors() throws Exception {
        shipmentService.expect(requestTo("http://shipment.test/shipments"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {"type": "urn:delivery:problem:validation-failed", "status": 400,
                                 "detail": "Request validation failed",
                                 "errors": [{"field": "recipientPhone", "message": "must be a valid phone number"}]}
                                """));

        mockMvc.perform(create("key-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("recipientPhone"));
    }

    @Test
    void reportsUnavailableShipmentService() throws Exception {
        shipmentService.expect(requestTo("http://shipment.test/shipments"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        mockMvc.perform(create("key-1")).andExpect(status().isServiceUnavailable());
    }

    @Test
    void requiresIdempotencyKey() throws Exception {
        mockMvc.perform(local(post("/api/shipments")).contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isBadRequest());
        shipmentService.verify();
    }

    @Test
    void rejectsNonLocalCallers() throws Exception {
        mockMvc.perform(post("/api/shipments/quotes").with(request -> {
                    request.setRemoteAddr("203.0.113.5");
                    return request;
                }).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        shipmentService.verify();
    }

    private MockHttpServletRequestBuilder create(String key) {
        return local(post("/api/shipments")).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY);
    }

    private static MockHttpServletRequestBuilder local(MockHttpServletRequestBuilder builder) {
        return builder.with(request -> {
            request.setRemoteAddr("127.0.0.1");
            return request;
        });
    }
}
