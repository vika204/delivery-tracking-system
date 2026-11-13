package com.delivery.webui.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class CourierDeliveryDetailsPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void opensCourierDeliveryDetailsForRequestedShipment() throws Exception {
        mockMvc.perform(get("/courier/deliveries/2154"))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/courier-delivery-details"))
                .andExpect(model().attribute("shipmentId", 2154L))
                .andExpect(content().string(containsString("SHIPMENT #2154")))
                .andExpect(content().string(containsString("BACK TO DASHBOARD")))
                .andExpect(content().string(containsString("DELIVERY")))
                .andExpect(content().string(containsString("PACKAGE")))
                .andExpect(content().string(containsString("CONFIRM PICKUP")))
                .andExpect(content().string(containsString("id=\"we-modal\"")))
                .andExpect(content().string(containsString("/css/components/modal.css")))
                .andExpect(content().string(containsString("/js/components/modal.js")))
                .andExpect(content().string(containsString(
                        "src=\"/js/pages/courier-delivery-details.js\""
                )));
    }

    @Test
    void rejectsNonPositiveShipmentId() throws Exception {
        mockMvc.perform(get("/courier/deliveries/0"))
                .andExpect(status().isNotFound());
    }
}
