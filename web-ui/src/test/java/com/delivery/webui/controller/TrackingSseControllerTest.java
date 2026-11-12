package com.delivery.webui.controller;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "tracking.stream.enabled=true",
        "tracking.stream.timeout=5m"
})
@AutoConfigureMockMvc
class TrackingSseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TrackingStreamService streamService;

    @Test
    void returnsEmitterWithoutBlockingHttpRequestThread() throws Exception {
        SseEmitter emitter = new SseEmitter(300_000L);
        when(streamService.subscribe(15)).thenReturn(emitter);

        mockMvc.perform(get("/api/tracking/shipments/15/events")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        verify(streamService).subscribe(15);
        emitter.complete();
    }

    @Test
    void rejectsInvalidShipmentIdBeforeOpeningStream() throws Exception {
        mockMvc.perform(get("/api/tracking/shipments/0/events")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isBadRequest())
                .andExpect(request().asyncNotStarted());

        verifyNoInteractions(streamService);
    }

    @Test
    void rejectsNonLoopbackClient() throws Exception {
        mockMvc.perform(get("/api/tracking/shipments/15/events")
                        .with(request -> {
                            request.setRemoteAddr("192.0.2.10");
                            return request;
                        }))
                .andExpect(status().isForbidden())
                .andExpect(request().asyncNotStarted());

        verifyNoInteractions(streamService);
    }
}
