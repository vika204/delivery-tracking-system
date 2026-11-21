package com.delivery.webui.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OfflineResourcesTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void anonymousVisitorsCanLoadTheGameAndPrecacheResources() throws Exception {
        mockMvc.perform(get("/offline.html").accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("SERVER TEMPORARILY UNAVAILABLE")))
                .andExpect(content().string(containsString(">SPIN</button>")))
                .andExpect(content().string(containsString("id=\"machine\"")))
                .andExpect(content().string(not(containsString("КРУТИТИ"))));
        for (String path : new String[]{"/service-worker.js", "/js/components/offline.js", "/images/spider-icon.svg"}) {
            mockMvc.perform(get(path)).andExpect(status().isOk());
        }
    }

    @Test
    void theSharedHeaderStartsPrecacheOnTheLandingPage() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("src=\"/js/components/offline.js\"")));
    }
}
