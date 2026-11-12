package com.delivery.webui.controller;

import jakarta.servlet.RequestDispatcher;
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
class ErrorPageRenderingTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rendersNotFoundPageThroughSpringBootErrorHandling() throws Exception {
        mockMvc.perform(get("/error")
                        .accept(MediaType.TEXT_HTML)
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404)
                        .requestAttr(
                                RequestDispatcher.ERROR_REQUEST_URI,
                                "/missing-page"
                        ))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("PAGE NOT FOUND")))
                .andExpect(content().string(containsString(
                        "src=\"/videos/error-courier.webm\""
                )))
                .andExpect(content().string(not(containsString(
                        "BACK TO HOME"
                ))))
                .andExpect(content().string(not(containsString(
                        "TRACK A PACKAGE"
                ))));
    }

    @Test
    void rendersServerErrorWithoutExposingInternalDetails() throws Exception {
        mockMvc.perform(get("/error")
                        .accept(MediaType.TEXT_HTML)
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
                        .requestAttr(
                                RequestDispatcher.ERROR_REQUEST_URI,
                                "/failed-page"
                        )
                        .requestAttr(
                                RequestDispatcher.ERROR_MESSAGE,
                                "sensitive internal details"
                        ))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(containsString(
                        "INTERNAL SERVER ERROR"
                )))
                .andExpect(content().string(not(containsString(
                        "BACK TO HOME"
                ))))
                .andExpect(content().string(not(containsString(
                        "TRACK A PACKAGE"
                ))))
                .andExpect(content().string(not(containsString(
                        "sensitive internal details"
                ))));
    }
}
