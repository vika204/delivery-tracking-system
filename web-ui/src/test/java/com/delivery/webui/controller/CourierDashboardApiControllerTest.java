package com.delivery.webui.controller;

import com.delivery.webui.controller.support.LoopbackInterceptor;
import com.delivery.webui.config.CourierDashboardProperties;
import com.delivery.webui.client.DispatchCourierDashboardClient;
import com.delivery.webui.controller.dto.CourierDashboardView;
import com.delivery.webui.exception.ApiExceptionHandler;
import com.delivery.webui.service.CourierDashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class CourierDashboardApiControllerTest {

    private static final long COURIER = 7;

    private final CourierDashboardService service = mock(CourierDashboardService.class);
    private final MockMvc mockMvc = standaloneSetup(new CourierDashboardApiController(service, new CourierDashboardProperties(COURIER)))
            .setControllerAdvice(new ApiExceptionHandler()).addInterceptors(new LoopbackInterceptor()).build();

    @Test
    void returnsDashboardOfConfiguredCourier() throws Exception {
        when(service.view(COURIER)).thenReturn(view("AVAILABLE", true));

        mockMvc.perform(get("/api/courier/dashboard").with(loopback()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("AVAILABLE"))
                .andExpect(jsonPath("$.online").value(true));
    }

    @Test
    void switchesAvailability() throws Exception {
        when(service.setAvailability(COURIER, false)).thenReturn(view("UNAVAILABLE", false));

        mockMvc.perform(put("/api/courier/dashboard/availability").with(loopback())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"online\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("UNAVAILABLE"));
    }

    @Test
    void acceptReturnsBusyDashboard() throws Exception {
        when(service.acceptOffer(COURIER, 55)).thenReturn(view("BUSY", true));

        mockMvc.perform(post("/api/courier/dashboard/offers/55/accept").with(loopback()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("BUSY"));
    }

    @Test
    void passesDispatchRejectionReasonToPage() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer dispatch = MockRestServiceServer.bindTo(builder).build();
        dispatch.expect(requestTo("http://dispatch.test/couriers/7/offers/55/accept"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {"type": "urn:delivery:problem:dispatch-conflict", "status": 409,
                                 "detail": "Courier 7 is offline: go online to accept offers"}
                                """));
        CourierDashboardService realService = new CourierDashboardService(
                HttpServiceProxyFactory.builderFor(RestClientAdapter.create(builder.baseUrl("http://dispatch.test").build()))
                .build().createClient(DispatchCourierDashboardClient.class));
        MockMvc wired = standaloneSetup(new CourierDashboardApiController(realService, new CourierDashboardProperties(COURIER)))
                .setControllerAdvice(new ApiExceptionHandler()).addInterceptors(new LoopbackInterceptor()).build();

        wired.perform(post("/api/courier/dashboard/offers/55/accept").with(loopback()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Courier 7 is offline: go online to accept offers"));
        dispatch.verify();
    }

    @Test
    void reportsUnavailableDispatchService() throws Exception {
        when(service.view(COURIER)).thenThrow(new ResourceAccessException("connection refused"));

        mockMvc.perform(get("/api/courier/dashboard").with(loopback()))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void rejectsNonLocalCallers() throws Exception {
        mockMvc.perform(get("/api/courier/dashboard").with(request -> {
                    request.setRemoteAddr("203.0.113.5");
                    return request;
                }))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    private static RequestPostProcessor loopback() {
        return request -> {
            request.setRemoteAddr("127.0.0.1");
            return request;
        };
    }

    private static CourierDashboardView view(String state, boolean online) {
        return new CourierDashboardView(COURIER, "Olena", online, state, null, List.of());
    }
}
