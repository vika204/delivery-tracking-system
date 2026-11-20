package com.delivery.webui.ratelimit;

import com.delivery.webui.client.NotificationClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"shipment.creation.sender-id=42", "rate-limit.capacity=2", "rate-limit.window=1h"})
@AutoConfigureMockMvc
class RateLimitIntegrationTest {

    private static final String API = "/api/notifications/unread";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private NotificationClient client;

    @Test
    void rejectsRequestsOverTheLimitWithProblemJsonAndRetryAfter() throws Exception {
        String ip = "127.0.0.1";

        mvc.perform(get(API).with(from(ip)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Limit", "2"))
                .andExpect(header().string("X-RateLimit-Remaining", "1"));
        mvc.perform(get(API).with(from(ip))).andExpect(status().isOk());

        mvc.perform(get(API).with(from(ip)))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.type").value("urn:delivery:problem:rate-limit-exceeded"))
                .andExpect(jsonPath("$.title").value("Rate limit exceeded"));
    }

    @Test
    void limitsEachClientAddressSeparately() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(get(API).with(from("127.0.0.2")));
        }

        mvc.perform(get(API).with(from("127.0.0.2"))).andExpect(status().isTooManyRequests());
        mvc.perform(get(API).with(from("127.0.0.3"))).andExpect(status().isOk());
    }

    @Test
    void doesNotLimitHtmlPages() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(get("/").with(from("127.0.0.4"))).andExpect(status().isOk());
        }
    }

    private static RequestPostProcessor from(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }
}
