package com.delivery.webui.controller;

import com.delivery.webui.controller.support.LoopbackInterceptor;
import com.delivery.webui.config.CourierDashboardProperties;
import com.delivery.webui.client.CourierShipmentClient;
import com.delivery.webui.client.TrackingLocationClient;
import com.delivery.webui.exception.ApiExceptionHandler;
import com.delivery.webui.service.CourierDeliveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class CourierDeliveryApiControllerTest {

    private static final long COURIER = 7;
    private static final String POINT = """
            {"pointId": "8c7b2f9e-1d3a-4c5b-9e8f-7a6b5c4d3e2f", "latitude": 50.4501, "longitude": 30.5234,
             "recordedAt": "2026-11-19T10:00:00Z"}
            """;

    private MockRestServiceServer shipmentService;
    private MockRestServiceServer trackingService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        RestClient.Builder shipment = RestClient.builder();
        RestClient.Builder tracking = RestClient.builder();
        shipmentService = MockRestServiceServer.bindTo(shipment).build();
        trackingService = MockRestServiceServer.bindTo(tracking).build();
        CourierDeliveryService service = new CourierDeliveryService(
                HttpServiceProxyFactory.builderFor(RestClientAdapter.create(shipment.baseUrl("http://shipment.test").build()))
                .build().createClient(CourierShipmentClient.class),
                HttpServiceProxyFactory.builderFor(RestClientAdapter.create(tracking.baseUrl("http://tracking.test").build()))
                .build().createClient(TrackingLocationClient.class));
        mockMvc = standaloneSetup(new CourierDeliveryApiController(service, new CourierDashboardProperties(COURIER)))
                .setControllerAdvice(new ApiExceptionHandler()).addInterceptors(new LoopbackInterceptor())
                .build();
    }

    @Test
    void showsAssignedShipmentAsCourier() throws Exception {
        expectCourierView("ASSIGNED");

        mockMvc.perform(local(get("/api/courier/shipments/15")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.recipient.initials").value("M. S."));
        shipmentService.verify();
    }

    @Test
    void confirmPickupMovesShipmentToInTransit() throws Exception {
        shipmentService.expect(requestTo("http://shipment.test/shipments/15/pickup"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-User-Id", "7"))
                .andRespond(withSuccess("{\"shipmentId\": 15, \"status\": \"IN_TRANSIT\"}", MediaType.APPLICATION_JSON));
        expectCourierView("IN_TRANSIT");

        mockMvc.perform(local(post("/api/courier/shipments/15/pickup")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"));
        shipmentService.verify();
    }

    @Test
    void pickupOfAnotherCouriersShipmentIsForbidden() throws Exception {
        shipmentService.expect(requestTo("http://shipment.test/shipments/15/pickup"))
                .andRespond(problem(HttpStatus.FORBIDDEN, "urn:delivery:problem:shipment-access-denied",
                        "Courier 7 is not assigned to shipment 15"));

        mockMvc.perform(local(post("/api/courier/shipments/15/pickup")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Courier 7 is not assigned to shipment 15"));
    }

    @Test
    void recordsGpsPointForShipmentInTransit() throws Exception {
        expectCourierView("IN_TRANSIT");
        trackingService.expect(requestTo("http://tracking.test/tracking/shipments/15/locations"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json(POINT))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body("""
                        {"locationId": 101, "shipmentId": 15, "latitude": 50.4501, "longitude": 30.5234,
                         "recordedAt": "2026-11-19T10:00:00"}
                        """));

        mockMvc.perform(point())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.locationId").value(101));
        shipmentService.verify();
        trackingService.verify();
    }

    @Test
    void reportsRepeatedPointWithoutDuplicate() throws Exception {
        expectCourierView("IN_TRANSIT");
        trackingService.expect(requestTo("http://tracking.test/tracking/shipments/15/locations"))
                .andExpect(content().json(POINT))
                .andRespond(withSuccess("""
                        {"locationId": 101, "shipmentId": 15, "latitude": 50.4501, "longitude": 30.5234,
                         "recordedAt": "2026-11-19T10:00:00"}
                        """, MediaType.APPLICATION_JSON));

        mockMvc.perform(point())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locationId").value(101));
    }

    @Test
    void refusesGpsBeforePickup() throws Exception {
        expectCourierView("ASSIGNED");
        trackingService.expect(never(), anything());

        mockMvc.perform(point())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:delivery:problem:shipment-not-in-transit"));
        trackingService.verify();
    }

    @Test
    void refusesGpsForAnotherCouriersShipment() throws Exception {
        shipmentService.expect(requestTo("http://shipment.test/shipments/15/courier-view"))
                .andRespond(problem(HttpStatus.FORBIDDEN, "urn:delivery:problem:shipment-access-denied",
                        "Courier 7 is not assigned to shipment 15"));
        trackingService.expect(never(), anything());

        mockMvc.perform(point()).andExpect(status().isForbidden());
        trackingService.verify();
    }

    @Test
    void passesTrackingValidationError() throws Exception {
        expectCourierView("IN_TRANSIT");
        trackingService.expect(requestTo("http://tracking.test/tracking/shipments/15/locations"))
                .andRespond(problem(HttpStatus.BAD_REQUEST, "urn:delivery:problem:location-invalid",
                        "recordedAt must not be in the future"));

        mockMvc.perform(point())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("recordedAt must not be in the future"));
    }

    @Test
    void completesDelivery() throws Exception {
        shipmentService.expect(requestTo("http://shipment.test/shipments/15/deliver"))
                .andRespond(withNoContent());
        expectCourierView("DELIVERED");

        mockMvc.perform(local(post("/api/courier/shipments/15/deliver")))
                .andExpect(jsonPath("$.status").value("DELIVERED"));
    }

    @Test
    void rejectsNonLocalCallers() throws Exception {
        mockMvc.perform(get("/api/courier/shipments/15").with(request -> {
                    request.setRemoteAddr("203.0.113.5");
                    return request;
                }))
                .andExpect(status().isForbidden());
        shipmentService.verify();
    }

    private void expectCourierView(String status) {
        shipmentService.expect(requestTo("http://shipment.test/shipments/15/courier-view"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-User-Id", "7"))
                .andRespond(withSuccess("""
                        {"shipmentId": 15, "status": "%s", "pickupAddress": "Київ, A", "deliveryAddress": "Київ, B",
                         "recipient": {"initials": "M. S.", "phone": "+380671234567"},
                         "weight": 2.4, "length": 40, "width": 30, "height": 20, "distance": 7.3, "price": 142,
                         "assignedAt": "2026-11-19T09:00:00Z"}
                        """.formatted(status), MediaType.APPLICATION_JSON));
    }

    private MockHttpServletRequestBuilder point() {
        return local(post("/api/courier/shipments/15/locations")).contentType(MediaType.APPLICATION_JSON).content(POINT);
    }

    private static org.springframework.test.web.client.response.DefaultResponseCreator problem(
            HttpStatus status, String type, String detail) {
        return withStatus(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body("""
                {"type": "%s", "status": %d, "detail": "%s"}
                """.formatted(type, status.value(), detail));
    }

    private static MockHttpServletRequestBuilder local(MockHttpServletRequestBuilder builder) {
        return builder.with(request -> {
            request.setRemoteAddr("127.0.0.1");
            return request;
        });
    }
}
