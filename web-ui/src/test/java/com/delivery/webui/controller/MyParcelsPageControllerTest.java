package com.delivery.webui.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class MyParcelsPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void showsApprovedParcelListDesign() throws Exception {
        mockMvc.perform(get("/my-parcels"))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/my-parcels"))
                .andExpect(content().string(not(containsString(
                        "Track and manage your deliveries."
                ))))
                .andExpect(content().string(containsString("SHIPMENT #15")))
                .andExpect(content().string(containsString("SHIPMENT #16")))
                .andExpect(content().string(containsString("SHIPMENT #17")))
                .andExpect(content().string(containsString("IN TRANSIT")))
                .andExpect(content().string(containsString("DELIVERED")))
                .andExpect(content().string(containsString("WAITING FOR COURIER")))
                .andExpect(content().string(containsString("href=\"/my-parcels/15\"")))
                .andExpect(content().string(containsString("href=\"/my-parcels/16\"")))
                .andExpect(content().string(containsString("href=\"/my-parcels/17\"")))
                .andExpect(content().string(containsString(
                        "href=\"/tracking?shipmentId=15&amp;previewRole=CLIENT\""
                )))
                .andExpect(content().string(containsString("web-button__web--left")))
                .andExpect(content().string(containsString("web-button__web--right")))
                .andExpect(content().string(containsString("data-profile-menu")))
                .andExpect(content().string(containsString("href=\"/my-parcels\"")));
    }

    @Test
    void showsShipmentDetails() throws Exception {
        mockMvc.perform(get("/my-parcels/15"))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/shipment-details"))
                .andExpect(content().string(containsString("BACK TO MY PARCELS")))
                .andExpect(content().string(containsString("SHIPMENT #15")))
                .andExpect(content().string(containsString("PICKUP ADDRESS")))
                .andExpect(content().string(containsString("Kharkiv, Sumska 10")))
                .andExpect(content().string(containsString("DELIVERY ADDRESS")))
                .andExpect(content().string(containsString("RECIPIENT PHONE")))
                .andExpect(content().string(containsString("CREATED AT")))
                .andExpect(content().string(containsString("DISTANCE")))
                .andExpect(content().string(containsString("WEIGHT")))
                .andExpect(content().string(containsString("DELIVERY PRICE")))
                .andExpect(content().string(containsString("COURIER PHONE")))
                .andExpect(content().string(containsString("Not available")))
                .andExpect(content().string(containsString(
                        "href=\"/tracking?shipmentId=15&amp;previewRole=CLIENT\""
                )))
                .andExpect(content().string(not(containsString("SHIPMENT DETAILS"))));
    }

    @Test
    void showsDeliveredShipmentDetails() throws Exception {
        mockMvc.perform(get("/my-parcels/16"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("SHIPMENT #16")))
                .andExpect(content().string(containsString("DELIVERED")))
                .andExpect(content().string(containsString("Olena Melnyk")));
    }

    @Test
    void returnsNotFoundForUnknownShipment() throws Exception {
        mockMvc.perform(get("/my-parcels/99999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void showsEmptyState() throws Exception {
        mockMvc.perform(get("/my-parcels").param("state", "empty"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("YOU HAVE NO PARCELS YET.")))
                .andExpect(content().string(containsString("CREATE A SHIPMENT")))
                .andExpect(content().string(not(containsString("SHIPMENT #15"))));
    }

    @Test
    void showsLoadingState() throws Exception {
        mockMvc.perform(get("/my-parcels").param("state", "loading"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("LOADING YOUR PARCELS...")))
                .andExpect(content().string(not(containsString("SHIPMENT #15"))));
    }

    @Test
    void showsErrorState() throws Exception {
        mockMvc.perform(get("/my-parcels").param("state", "error"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "PARCELS ARE TEMPORARILY UNAVAILABLE."
                )))
                .andExpect(content().string(not(containsString("SHIPMENT #15"))));
    }
}
