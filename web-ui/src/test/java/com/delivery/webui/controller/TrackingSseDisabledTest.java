package com.delivery.webui.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "tracking.stream.enabled=false")
@AutoConfigureMockMvc
class TrackingSseDisabledTest {

    @Autowired
    private MockMvc mockMvc;

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
}
