package com.delivery.webui.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "dispatch.courier-dashboard.enabled=false")
@AutoConfigureMockMvc
class CourierDeliveryApiDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiIsAbsentWhenFeatureFlagIsDisabled() throws Exception {
        mockMvc.perform(get("/api/courier/shipments/15").with(request -> {
                    request.setRemoteAddr("127.0.0.1");
                    return request;
                }))
                .andExpect(status().isNotFound());
    }
}
