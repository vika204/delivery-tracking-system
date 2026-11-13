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
class RegistrationPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void opensRegistrationPage() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/register"))
                .andExpect(content().string(containsString("CREATE CLIENT ACCOUNT")))
                .andExpect(content().string(containsString("CREATE COURIER ACCOUNT")))
                .andExpect(content().string(containsString("data-registration-tab=\"client\"")))
                .andExpect(content().string(containsString("data-registration-tab=\"courier\"")))
                .andExpect(content().string(containsString("EMAIL ADDRESS")))
                .andExpect(content().string(containsString("CONFIRM PASSWORD")))
                .andExpect(content().string(containsString("FULL NAME")))
                .andExpect(content().string(containsString("PHONE NUMBER")))
                .andExpect(content().string(containsString("WORK ZONE")))
                .andExpect(content().string(containsString("MAX PACKAGE WEIGHT")))
                .andExpect(content().string(containsString("courier-work-zone-suggestions")))
                .andExpect(content().string(containsString("katottg-autocomplete.css")))
                .andExpect(content().string(containsString("href=\"/login\"")))
                .andExpect(content().string(containsString("Log in")))
                .andExpect(content().string(not(containsString("name=\"password\""))));
    }

    @Test
    void opensLoginPrototypeWithoutExtraFields() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/login"))
                .andExpect(content().string(containsString(
                        "<span>WELCOME</span> <span>BACK.</span>"
                )))
                .andExpect(content().string(containsString("LOG IN")))
                .andExpect(content().string(containsString("href=\"/register\"")))
                .andExpect(content().string(containsString("Register")))
                .andExpect(content().string(not(containsString("confirm-password"))))
                .andExpect(content().string(not(containsString("Forgot password?"))))
                .andExpect(content().string(not(containsString("name=\"password\""))));
    }
}
