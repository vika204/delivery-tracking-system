package com.delivery.webui.controller;

import com.delivery.webui.client.ClientShipmentClient;
import com.delivery.webui.exception.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ClientShipmentApiControllerTest {

    private final ClientShipmentClient client = mock(ClientShipmentClient.class);
    private final MockMvc mvc = standaloneSetup(new ClientShipmentApiController(client, 42))
            .setControllerAdvice(new ApiExceptionHandler()).build();

    @Test
    void cancelsAsConfiguredSenderWithOneBackendRequest() throws Exception {
        mvc.perform(post("/api/shipments/515/cancel").header("X-User-Id", "99").param("senderId", "99"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"));
        verify(client).cancelShipment(42, 515);
        verifyNoMoreInteractions(client);
    }

    @ParameterizedTest
    @EnumSource(value = HttpStatus.class, names = {"FORBIDDEN", "NOT_FOUND", "CONFLICT"})
    void preservesBackendOwnershipAndStatusRejections(HttpStatus status) throws Exception {
        doThrow(HttpClientErrorException.create(status, status.getReasonPhrase(), HttpHeaders.EMPTY, new byte[0], null))
                .when(client).cancelShipment(42, 515);
        mvc.perform(post("/api/shipments/515/cancel"))
                .andExpect(status().is(status.value()))
                .andExpect(jsonPath("$.status").value(status.value()));
        verify(client).cancelShipment(42, 515);
        verifyNoMoreInteractions(client);
    }

    @Test
    void backendConnectionFailureIsNotReportedAsCancellation() throws Exception {
        doThrow(new ResourceAccessException("private-shipment-host")).when(client).cancelShipment(42, 515);
        mvc.perform(post("/api/shipments/515/cancel"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(not(containsString("private-shipment-host"))));
    }

    @Test
    void rejectsRemoteRequestsBeforeCallingBackend() throws Exception {
        mvc.perform(post("/api/shipments/515/cancel").with(request -> {
                    request.setRemoteAddr("203.0.113.5");
                    return request;
                })).andExpect(status().isForbidden());
        verifyNoInteractions(client);
    }

    @Test
    void invalidShipmentIdDoesNotCallBackend() throws Exception {
        mvc.perform(post("/api/shipments/abc/cancel")).andExpect(status().isBadRequest());
        verifyNoInteractions(client);
    }
}
