package com.delivery.webui.controller;

import com.delivery.webui.client.NotificationClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "shipment.creation.sender-id=42")
@AutoConfigureMockMvc
class NotificationApiControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private NotificationClient client;

    @Test
    void readsIndicatorForConfiguredClientIgnoringQueryIdentity() throws Exception {
        when(client.hasUnreadNotifications(42)).thenReturn(true);

        mvc.perform(get("/api/notifications/unread").param("userId", "99"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string("true"));
        verify(client).hasUnreadNotifications(42);
        verifyNoMoreInteractions(client);
    }

    @Test
    void marksReadAndReturnsRemainingUnreadWithoutExtraRequest() throws Exception {
        when(client.markNotificationsRead(42, 103)).thenReturn(false);

        mvc.perform(post("/api/notifications/read").param("throughId", "103").param("userId", "99"))
                .andExpect(status().isOk()).andExpect(content().string("false"));
        verify(client).markNotificationsRead(42, 103);
        verifyNoMoreInteractions(client);
    }

    @Test
    void preservesIndicatorWhenNewerNotificationsRemainUnread() throws Exception {
        when(client.markNotificationsRead(42, 103)).thenReturn(true);

        mvc.perform(post("/api/notifications/read").param("throughId", "103"))
                .andExpect(status().isOk()).andExpect(content().string("true"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc"})
    void rejectsInvalidBoundaryBeforeCallingBackend(String throughId) throws Exception {
        mvc.perform(post("/api/notifications/read").param("throughId", throughId))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(client);
    }

    @Test
    void rejectsNonLocalRequests() throws Exception {
        mvc.perform(get("/api/notifications/unread").with(request -> {
                    request.setRemoteAddr("203.0.113.5");
                    return request;
                })).andExpect(status().isForbidden());
        mvc.perform(post("/api/notifications/read").param("throughId", "103").with(request -> {
                    request.setRemoteAddr("203.0.113.5");
                    return request;
                })).andExpect(status().isForbidden());
        verifyNoInteractions(client);
    }

    @Test
    void backendFailureReturns503WithoutPretendingEverythingWasRead() throws Exception {
        when(client.hasUnreadNotifications(42)).thenThrow(new ResourceAccessException("private-backend-host"));

        mvc.perform(get("/api/notifications/unread"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.status").value(503))
                .andExpect(content().string(not(containsString("private-backend-host"))));
    }
}
