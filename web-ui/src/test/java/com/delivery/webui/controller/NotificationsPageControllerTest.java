package com.delivery.webui.controller;

import com.delivery.webui.client.NotificationClient;
import com.delivery.webui.client.dto.NotificationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest(properties = "shipment.creation.sender-id=42")
@AutoConfigureMockMvc
@WithMockUser(roles = "CLIENT")
class NotificationsPageControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private NotificationClient client;

    @Test
    void showsRealNotificationsInBackendOrderWithExistingDesignAndDetailsLinks() throws Exception {
        when(client.getMyNotifications(42, 50)).thenReturn(List.of(
                notification(3, "DELIVERED", "Your package has been delivered."),
                notification(2, "PACKAGE_PICKED_UP", "Your package has been picked up."),
                notification(1, "COURIER_ASSIGNED", "A courier has been assigned.")));

        String html = mvc.perform(get("/notifications"))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/notifications"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(model().attribute("activePage", "notifications"))
                .andExpect(model().attribute("headerState", "CLIENT"))
                .andExpect(content().string(containsString("SHIPMENT #15")))
                .andExpect(content().string(containsString("19 Nov 2026, 12:30")))
                .andExpect(content().string(containsString("href=\"/my-parcels/15?from=notifications\"")))
                .andExpect(content().string(containsString(">DETAILS</span>")))
                .andExpect(content().string(containsString("/css/pages/my-parcels.css")))
                .andExpect(content().string(containsString("web-button__web--left")))
                .andExpect(content().string(containsString("data-notification-read-through=\"3\"")))
                .andExpect(content().string(not(containsString("shipment-stamp-"))))
                .andExpect(content().string(containsString("site-header__desktop-only site-header__notifications active")))
                .andExpect(content().string(not(containsString("site-header__profile-button active"))))
                .andReturn().getResponse().getContentAsString();

        assertThat(html.indexOf("DELIVERED")).isLessThan(html.indexOf("PACKAGE PICKED UP"));
        assertThat(html.indexOf("PACKAGE PICKED UP")).isLessThan(html.indexOf("COURIER ASSIGNED"));
        verify(client).getMyNotifications(42, 50);
        verify(client, never()).markNotificationsRead(anyLong(), anyLong());
    }

    @Test
    void showsEmptyStateWithoutInventingNotifications() throws Exception {
        when(client.getMyNotifications(42, 50)).thenReturn(List.of());

        mvc.perform(get("/notifications"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("NO NOTIFICATIONS YET.")))
                .andExpect(content().string(containsString("data-notification-read-through=\"0\"")))
                .andExpect(content().string(not(containsString(">DETAILS</span>"))))
                .andExpect(content().string(not(containsString("TEMPORARILY UNAVAILABLE"))));
    }

    @Test
    void unavailableServiceShowsErrorInsteadOfEmptyStateOrInternalDetails() throws Exception {
        when(client.getMyNotifications(42, 50)).thenThrow(new ResourceAccessException("private-backend-host"));

        mvc.perform(get("/notifications"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(containsString("NOTIFICATIONS ARE TEMPORARILY UNAVAILABLE.")))
                .andExpect(content().string(not(containsString("NO NOTIFICATIONS YET."))))
                .andExpect(content().string(not(containsString("private-backend-host"))));
        verify(client, never()).markNotificationsRead(anyLong(), anyLong());
    }

    @Test
    void missingResponseBodyShowsLoadingError() throws Exception {
        when(client.getMyNotifications(42, 50)).thenReturn(null);

        mvc.perform(get("/notifications"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(containsString("NOTIFICATIONS ARE TEMPORARILY UNAVAILABLE.")));
    }

    @Test
    void doesNotTakeClientIdentityFromQueryParameters() throws Exception {
        when(client.getMyNotifications(42, 50)).thenReturn(List.of());

        mvc.perform(get("/notifications").param("userId", "99")).andExpect(status().isOk());
        verify(client).getMyNotifications(42, 50);
    }

    @Test
    void rejectsNonLocalAccessBeforeCallingBackend() throws Exception {
        mvc.perform(get("/notifications").with(request -> {
                    request.setRemoteAddr("203.0.113.5");
                    return request;
                })).andExpect(status().isForbidden());
        verifyNoInteractions(client);
    }

    @Test
    void escapesNotificationText() throws Exception {
        when(client.getMyNotifications(42, 50)).thenReturn(List.of(notification(1, "DELIVERED", "<script>unsafe</script>")));

        mvc.perform(get("/notifications"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;script&gt;unsafe&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("<script>unsafe</script>"))));
    }

    @Test
    void guestAndCourierNavigationDoNotGainClientNotificationsLink() throws Exception {
        mvc.perform(get("/").with(anonymous())).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("href=\"/notifications\""))));
        mvc.perform(get("/courier/profile").with(user("courier").roles("COURIER"))).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("href=\"/notifications\""))));
        verifyNoInteractions(client);
    }

    @Test
    void showsNewBellOnlyOnUnreadCards() throws Exception {
        when(client.getMyNotifications(42, 50)).thenReturn(List.of(
                notification(2, "DELIVERED", "New delivery update."),
                new NotificationResponse(1, 15L, "COURIER_ASSIGNED", "Already viewed.",
                        LocalDateTime.of(2026, 11, 19, 10, 0), true)));

        String html = mvc.perform(get("/notifications")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html.split("class=\"notification-new-icon\"", -1)).hasSize(2);
        assertThat(html).contains("New delivery update.", "Already viewed.");
    }

    @Test
    void pollingReturnsOnlyTheExistingNotificationFragmentWithStableIds() throws Exception {
        when(client.getMyNotifications(42, 50)).thenReturn(List.of(
                notification(3, "DELIVERED", "<script>unsafe</script>")));

        mvc.perform(get("/notifications").param("fragment", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("pages/notifications :: notificationContent"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(containsString("data-notification-id=\"3\"")))
                .andExpect(content().string(containsString("data-notification-read-through=\"3\"")))
                .andExpect(content().string(containsString("&lt;script&gt;unsafe&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("<html"))))
                .andExpect(content().string(not(containsString("site-header"))));
        verify(client, never()).markNotificationsRead(anyLong(), anyLong());
    }

    @Test
    void usesHighestLoadedIdRatherThanFirstTimestampSortedItemForReadBoundary() throws Exception {
        when(client.getMyNotifications(42, 50)).thenReturn(List.of(
                notification(1, "DELIVERED", "Newest timestamp."),
                new NotificationResponse(5, 15L, "PACKAGE_PICKED_UP", "Older timestamp.",
                        LocalDateTime.of(2026, 11, 19, 10, 0), false)));

        mvc.perform(get("/notifications"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-notification-read-through=\"5\"")));
    }

    private NotificationResponse notification(long id, String type, String message) {
        return new NotificationResponse(id, 15L, type, message, LocalDateTime.of(2026, 11, 19, 12, 27 + (int) id), false);
    }
}
