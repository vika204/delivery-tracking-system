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
class CourierProfilePageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void opensCourierProfilePrototype() throws Exception {
        mockMvc.perform(get("/courier/profile"))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/courier-profile"))
                .andExpect(content().string(containsString("Andrii Kovalenko")))
                .andExpect(content().string(containsString("andrii@gmail.com")))
                .andExpect(content().string(containsString("+380 67 123 45 67")))
                .andExpect(content().string(containsString("WORK ZONE")))
                .andExpect(content().string(containsString("MAX PACKAGE WEIGHT")))
                .andExpect(content().string(containsString("courier-dashboard-portrait.webp")))
                .andExpect(content().string(containsString("courier-profile.js")))
                .andExpect(content().string(containsString("site-header")))
                .andExpect(content().string(containsString("href=\"/courier/dashboard\"")))
                .andExpect(content().string(containsString("href=\"/courier/profile\"")));
    }

    @Test
    void servesOfficialKatottgSettlementData() throws Exception {
        mockMvc.perform(get("/data/katottg-settlements.json"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(content().string(containsString("\"version\":\"2026-07-07\"")))
                .andExpect(content().string(containsString("\"Київ\"")))
                .andExpect(content().string(not(containsString("\"H\""))));
    }
}
