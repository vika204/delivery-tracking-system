package com.delivery.webui.exception;

import com.delivery.webui.controller.support.LoopbackInterceptor;
import com.delivery.webui.config.CourierDashboardProperties;
import com.delivery.webui.config.ShipmentCreationProperties;
import com.delivery.webui.client.ShipmentCreationClient;
import com.delivery.webui.client.TrackingShipmentClient;
import com.delivery.webui.controller.CourierDashboardApiController;
import com.delivery.webui.controller.ShipmentCreationApiController;
import com.delivery.webui.controller.TrackingStatusController;
import com.delivery.webui.service.CourierDashboardService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.context.request.ServletWebRequest;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ApiExceptionHandlerTest {

    private final CourierDashboardService dashboard = mock(CourierDashboardService.class);
    private final ShipmentCreationClient creation = mock(ShipmentCreationClient.class);
    private final TrackingShipmentClient tracking = mock(TrackingShipmentClient.class);
    private final MockMvc mvc = standaloneSetup(
            new CourierDashboardApiController(dashboard, new CourierDashboardProperties(7)),
            new ShipmentCreationApiController(creation, new ShipmentCreationProperties(42)),
            new TrackingStatusController(tracking)
    ).setControllerAdvice(new ApiExceptionHandler()).addInterceptors(new LoopbackInterceptor()).build();

    @Test
    void malformedJsonReturnsProblemDetailWithoutCallingBackend() throws Exception {
        mvc.perform(post("/api/shipments/quotes").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.instance").value("/api/shipments/quotes"));
        verifyNoInteractions(creation);
    }

    @Test
    void missingIdempotencyHeaderReturnsProblemDetail() throws Exception {
        mvc.perform(post("/api/shipments").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(creation);
    }

    @Test
    void invalidPathVariableReturnsProblemDetail() throws Exception {
        mvc.perform(get("/api/tracking/shipments/abc/status"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(tracking);
    }

    @Test
    void forbiddenLocalDevelopmentAccessReturnsProblemDetail() throws Exception {
        mvc.perform(get("/api/courier/dashboard").with(request -> {
                    request.setRemoteAddr("203.0.113.5");
                    return request;
                }))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403));
        verifyNoInteractions(dashboard);
    }

    @ParameterizedTest
    @EnumSource(value = HttpStatus.class, names = {"BAD_REQUEST", "FORBIDDEN", "NOT_FOUND", "CONFLICT", "UNPROCESSABLE_CONTENT"})
    void preservesBackendClientErrorStatusWithoutExposingUnreadableBody(HttpStatus code) throws Exception {
        when(dashboard.view(7)).thenThrow(HttpClientErrorException.create(code, code.getReasonPhrase(),
                HttpHeaders.EMPTY, "unreadable-private-backend-body".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

        mvc.perform(get("/api/courier/dashboard"))
                .andExpect(status().is(code.value()))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(code.value()))
                .andExpect(content().string(not(containsString("unreadable-private-backend-body"))));
    }

    @Test
    void backendServerFailureReturns503WithoutInternalDetails() throws Exception {
        when(dashboard.view(7)).thenThrow(HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error", HttpHeaders.EMPTY, "private-database-error".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8));

        mvc.perform(get("/api/courier/dashboard"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(content().string(not(containsString("private-database-error"))));
    }

    @Test
    void backendConnectionFailureReturns503() throws Exception {
        when(tracking.getShipmentById(15)).thenThrow(
                new ResourceAccessException("private-service-address", new ConnectException("Connection refused")));

        mvc.perform(get("/api/tracking/shipments/15/status"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(content().string(not(containsString("private-service-address"))));
    }

    @ParameterizedTest
    @MethodSource("timeouts")
    void backendTimeoutReturns504WithoutInternalDetails(IOException cause) throws Exception {
        when(tracking.getShipmentById(15)).thenThrow(new ResourceAccessException("private-service-address", cause));

        mvc.perform(get("/api/tracking/shipments/15/status"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(504))
                .andExpect(jsonPath("$.detail").value("Service response timed out"))
                .andExpect(content().string(not(containsString("private-service-address"))));
    }

    static Stream<IOException> timeouts() {
        HttpConnectTimeoutException connectTimeout = new HttpConnectTimeoutException("Connection timed out");
        connectTimeout.initCause(new ConnectException("Connection timed out"));
        return Stream.of(new HttpTimeoutException("Request timed out"), new SocketTimeoutException("Read timed out"),
                new HttpConnectTimeoutException("Connection timed out"), new IOException("HTTP request failed", connectTimeout));
    }

    @Test
    void unexpectedErrorReturnsSafe500ProblemDetail() throws Exception {
        when(dashboard.view(7)).thenThrow(new IllegalStateException("private-internal-error"));

        mvc.perform(get("/api/courier/dashboard"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred. Please try again later."))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(content().string(not(containsString("private-internal-error"))));
    }

    @Test
    void doesNotReplaceCommittedSseResponseWithJson() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        response.setCommitted(true);

        var result = new ApiExceptionHandler().handleUnexpected(new IllegalStateException("stream already closed"),
                new ServletWebRequest(new MockHttpServletRequest(), response));

        assertThat(result).isNull();
        assertThat(response.getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(response.getContentAsByteArray()).isEmpty();
    }
}
