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
class ShipmentCreatedPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void displaysCreatedShipmentIdAndNavigation() throws Exception {
        mockMvc.perform(get("/ship/success/18"))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/shipment-created"))
                .andExpect(model().attribute("headerState", "CLIENT"))
                .andExpect(model().attribute("activePage", "ship"))
                .andExpect(model().attribute("shipmentId", 18L))
                .andExpect(content().string(containsString("SHIPMENT")))
                .andExpect(content().string(containsString("#18")))
                .andExpect(content().string(containsString("CREATED!")))
                .andExpect(content().string(containsString("href=\"/my-parcels\"")))
                .andExpect(content().string(containsString(
                        "href=\"/tracking?shipmentId=18&amp;previewRole=CLIENT\""
                )))
                .andExpect(content().string(containsString("shipment-stamp-ukraine-cat.png")))
                .andExpect(content().string(containsString("shipment-stamp-japan-kitten.png")))
                .andExpect(content().string(containsString("shipment-stamp-guinea-kitten.png")));
    }

    @Test
    void rejectsNonPositiveShipmentId() throws Exception {
        mockMvc.perform(get("/ship/success/0"))
                .andExpect(status().isNotFound());
    }
}
