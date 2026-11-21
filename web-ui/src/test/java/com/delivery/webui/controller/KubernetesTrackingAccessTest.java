package com.delivery.webui.controller;

import com.delivery.webui.client.TrackingShipmentClient;
import com.delivery.webui.client.dto.TrackingShipmentResponse;
import com.delivery.webui.service.TrackingStreamService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "web.local-dev.loopback-only=false",
        "tracking.stream.enabled=true"
})
@AutoConfigureMockMvc
class KubernetesTrackingAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TrackingShipmentClient shipmentClient;

    @MockitoBean
    private TrackingStreamService streamService;

    @Test
    void readsStatusThroughTheClusterProxy() throws Exception {
        when(shipmentClient.getShipmentById(15L))
                .thenReturn(new TrackingShipmentResponse(15L, "Test Recipient", "DELIVERED"));

        mockMvc.perform(get("/api/tracking/shipments/15/status")
                        .with(request -> {
                            request.setRemoteAddr("10.244.0.10");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));
    }

    @Test
    void streamsCoordinatesThroughTheClusterProxy() throws Exception {
        SseEmitter emitter = new SseEmitter(300_000L);
        when(streamService.subscribe(15L)).thenReturn(emitter);

        mockMvc.perform(get("/api/tracking/shipments/15/events")
                        .with(request -> {
                            request.setRemoteAddr("10.244.0.10");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        verify(streamService).subscribe(15L);
        emitter.complete();
    }

    @Test
    void keepsValidationAndAuthenticationInTheCluster() throws Exception {
        mockMvc.perform(get("/api/tracking/shipments/0/status"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/shipments/mine"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(shipmentClient, streamService);
    }
}
