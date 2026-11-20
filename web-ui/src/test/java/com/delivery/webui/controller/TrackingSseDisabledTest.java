package com.delivery.webui.controller;

import com.delivery.webui.client.TrackingShipmentClient;
import com.delivery.webui.client.dto.TrackingShipmentResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "tracking.stream.enabled=false")
@AutoConfigureMockMvc
class TrackingSseDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TrackingShipmentClient shipmentClient;

    @Test
    void endpointIsUnavailableWhenFeatureFlagIsDisabled() throws Exception {
        mockMvc.perform(get("/api/tracking/shipments/15/events")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isNotFound())
                .andExpect(request().asyncNotStarted());
    }

    @Test
    void statusRemainsAvailableWithoutGpsStreaming() throws Exception {
        when(shipmentClient.getShipmentById(15))
                .thenReturn(new TrackingShipmentResponse(15L, "Private recipient", "ASSIGNED"));

        mockMvc.perform(get("/api/tracking/shipments/15/status")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipmentId").value(15))
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.recipientName").doesNotExist())
                .andExpect(request().asyncNotStarted());
    }

    @Test
    void locationBackfillEndpointIsUnavailableWhenFeatureFlagIsDisabled()
            throws Exception {
        mockMvc.perform(get("/api/tracking/shipments/15/locations")
                        .param("afterLocationId", "10")
                        .param("limit", "100")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isNotFound())
                .andExpect(request().asyncNotStarted());
    }

}
