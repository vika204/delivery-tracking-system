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
class ProfilePageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void opensProfilePrototype() throws Exception {
        mockMvc.perform(get("/profile"))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/profile"))
                .andExpect(model().attribute("headerState", "CLIENT"))
                .andExpect(model().attribute("activePage", "profile"))
                .andExpect(content().string(containsString("MY PROFILE")))
                .andExpect(content().string(containsString("Olena Kovalenko")))
                .andExpect(content().string(containsString("src=\"/images/profile-spider.png\"")))
                .andExpect(content().string(containsString("EDIT PROFILE")))
                .andExpect(content().string(containsString("src=\"/js/pages/profile.js\"")));
    }
}
