package com.delivery.webui.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "shipment.creation.enabled=false")
@AutoConfigureMockMvc
class ShipmentCreationApiDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiIsAbsentWhenFeatureFlagIsDisabled() throws Exception {
        mockMvc.perform(post("/api/shipments/quotes").with(request -> {
                    request.setRemoteAddr("127.0.0.1");
                    return request;
                }).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }
}
