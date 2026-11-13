package com.delivery.webui.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class CourierDashboardPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void opensCourierDashboardPrototype() throws Exception {
        mockMvc.perform(get("/courier/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/courier-dashboard"))
                .andExpect(content().string(containsString("DELIVERY OFFER")))
                .andExpect(content().string(containsString("ACTIVE DELIVERIES")))
                .andExpect(content().string(containsString("SHIPMENT #2187")))
                .andExpect(content().string(containsString("SHIPMENT #2154")))
                .andExpect(content().string(containsString("courier-dashboard-portrait.webp")))
                .andExpect(content().string(containsString("src=\"/js/pages/courier-dashboard.js\"")));
    }
}
