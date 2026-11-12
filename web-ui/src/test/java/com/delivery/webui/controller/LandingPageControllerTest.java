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
class LandingPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void opensLandingPageWithApprovedHeroAndSharedHeader() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/landing"))
                .andExpect(content().string(containsString("WITH GREAT SPEED")))
                .andExpect(content().string(containsString("COMES GREAT DELIVERY")))
                .andExpect(content().string(containsString("SEND A PACKAGE")))
                .andExpect(content().string(containsString(
                        "src=\"/images/landing-courier.webp\""
                )))
                .andExpect(content().string(containsString("id=\"cursor-webs\"")))
                .andExpect(content().string(containsString("href=\"/login\"")))
                .andExpect(content().string(containsString("href=\"/register\"")))
                .andExpect(content().string(not(containsString(">PRICING<"))))
                .andExpect(content().string(not(containsString(">CONTACT<"))));
    }
}
