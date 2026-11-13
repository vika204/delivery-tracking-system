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
class CreateShipmentPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void opensCreateShipmentPrototype() throws Exception {
        mockMvc.perform(get("/ship"))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/create-shipment"))
                .andExpect(model().attribute("headerState", "CLIENT"))
                .andExpect(model().attribute("activePage", "ship"))
                .andExpect(content().string(containsString("CREATE SHIPMENT")))
                .andExpect(content().string(containsString("DELIVERY ROUTE")))
                .andExpect(content().string(containsString("RECIPIENT DETAILS")))
                .andExpect(content().string(containsString("PACKAGE DETAILS")))
                .andExpect(content().string(containsString("ORDER SUMMARY")))
                .andExpect(content().string(containsString("PICKUP CITY / VILLAGE")))
                .andExpect(content().string(containsString("DELIVERY CITY / VILLAGE")))
                .andExpect(content().string(containsString("pickup-city-suggestions")))
                .andExpect(content().string(containsString("delivery-city-suggestions")))
                .andExpect(content().string(containsString("katottg-autocomplete.css")))
                .andExpect(content().string(containsString("href=\"/ship\"")))
                .andExpect(content().string(containsString(
                        "src=\"/js/pages/create-shipment.js\""
                )))
                .andExpect(content().string(containsString("name=\"pickupAddress\"")))
                .andExpect(content().string(containsString("name=\"deliveryAddress\"")));
    }
}
