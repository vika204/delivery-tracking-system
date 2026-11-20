package com.delivery.webui.ratelimit;

import com.delivery.webui.client.NotificationClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"shipment.creation.sender-id=42", "rate-limit.enabled=false", "rate-limit.capacity=1"})
@AutoConfigureMockMvc
class RateLimitDisabledTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApplicationContext context;

    @MockitoBean
    private NotificationClient client;

    @Test
    void doesNotLimitWhenSwitchedOff() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/api/notifications/unread")).andExpect(status().isOk());
        }
        assertThat(context.getBeanNamesForType(TokenBucketRateLimiter.class)).isEmpty();
    }
}
