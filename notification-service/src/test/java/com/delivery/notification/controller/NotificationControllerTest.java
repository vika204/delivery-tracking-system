package com.delivery.notification.controller;

import com.delivery.notification.entity.Notification;
import com.delivery.notification.entity.NotificationType;
import com.delivery.notification.repository.NotificationRepository;
import com.delivery.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class NotificationControllerTest {

    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final MockMvc mvc = standaloneSetup(new NotificationController(new NotificationService(repository))).build();

    @Test
    void createsNotificationWithExistingRequestFormat() throws Exception {
        when(repository.save(any(Notification.class))).thenAnswer(call -> {
            Notification saved = call.getArgument(0);
            saved.setNotificationId(3L);
            saved.setCreatedAt(LocalDateTime.of(2026, 11, 19, 10, 3));
            return saved;
        });

        mvc.perform(post("/notifications").contentType(MediaType.APPLICATION_JSON).content("""
                        {"userId":42,"shipmentId":15,"type":"DELIVERED",
                         "message":"Package delivered","isRead":false}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationId").value(3))
                .andExpect(jsonPath("$.shipmentId").value(15))
                .andExpect(jsonPath("$.type").value("DELIVERED"))
                .andExpect(jsonPath("$.message").value("Package delivered"))
                .andExpect(jsonPath("$.read").value(false))
                .andExpect(jsonPath("$.userId").doesNotExist());

        var saved = org.mockito.ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(saved.capture());
        org.assertj.core.api.Assertions.assertThat(saved.getValue().getUserId()).isEqualTo(42L);
    }

    @Test
    void readsExistingListAndIndividualNotification() throws Exception {
        Notification notification = notification(2, NotificationType.DELIVERED);
        when(repository.findAll()).thenReturn(List.of(notification));
        when(repository.findById(2L)).thenReturn(Optional.of(notification));

        mvc.perform(get("/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].notificationId").value(2));
        mvc.perform(get("/notifications/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationId").value(2))
                .andExpect(jsonPath("$.type").value("DELIVERED"));
    }

    @Test
    void missingNotificationStillReturns404() throws Exception {
        mvc.perform(get("/notifications/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void readsOnlyRequestedUserNotificationsWithBoundedNewestFirstQuery() throws Exception {
        when(repository.findByUserIdOrderByCreatedAtDescNotificationIdDesc(eq(42L), any(Pageable.class)))
                .thenReturn(List.of(notification(2, NotificationType.DELIVERED), notification(1, NotificationType.COURIER_ASSIGNED)));

        mvc.perform(get("/notifications/mine").header("X-User-Id", "42").param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].notificationId").value(2))
                .andExpect(jsonPath("$[0].type").value("DELIVERED"))
                .andExpect(jsonPath("$[0].shipmentId").value(15))
                .andExpect(jsonPath("$[0].createdAt").exists())
                .andExpect(jsonPath("$[0].userId").doesNotExist())
                .andExpect(jsonPath("$[0].read").value(false))
                .andExpect(jsonPath("$[1].notificationId").value(1));

        var page = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByUserIdOrderByCreatedAtDescNotificationIdDesc(eq(42L), page.capture());
        org.assertj.core.api.Assertions.assertThat(page.getValue().getPageNumber()).isZero();
        org.assertj.core.api.Assertions.assertThat(page.getValue().getPageSize()).isEqualTo(20);
        verify(repository, never()).markReadThroughForUser(anyLong(), anyLong());
    }

    @Test
    void emptyHistoryReturnsEmptyArray() throws Exception {
        when(repository.findByUserIdOrderByCreatedAtDescNotificationIdDesc(eq(42L), any(Pageable.class)))
                .thenReturn(List.of());

        mvc.perform(get("/notifications/mine").header("X-User-Id", "42"))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void requiresUserHeader() throws Exception {
        mvc.perform(get("/notifications/mine")).andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc"})
    void rejectsInvalidUserId(String userId) throws Exception {
        mvc.perform(get("/notifications/mine").header("X-User-Id", userId)).andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "101", "-1", "abc"})
    void rejectsInvalidLimit(String limit) throws Exception {
        mvc.perform(get("/notifications/mine").header("X-User-Id", "42").param("limit", limit))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }

    @Test
    void unreadIndicatorUsesOnlyRequestedClient() throws Exception {
        when(repository.existsByUserIdAndIsReadFalse(42L)).thenReturn(true);

        mvc.perform(get("/notifications/mine/unread").header("X-User-Id", "42"))
                .andExpect(status().isOk()).andExpect(jsonPath("$").value(true));
        verify(repository).existsByUserIdAndIsReadFalse(42L);
        verify(repository, never()).markReadThroughForUser(anyLong(), anyLong());
    }

    @Test
    void marksClientHistoryThroughLoadedIdThenReturnsRemainingUnreadStatus() throws Exception {
        when(repository.existsByUserIdAndIsReadFalse(42L)).thenReturn(true);

        mvc.perform(post("/notifications/mine/read").header("X-User-Id", "42").param("throughId", "103"))
                .andExpect(status().isOk()).andExpect(jsonPath("$").value(true));

        var order = inOrder(repository);
        order.verify(repository).markReadThroughForUser(42L, 103L);
        order.verify(repository).existsByUserIdAndIsReadFalse(42L);
    }

    @Test
    void repeatedMarkReadSucceedsWhenEverythingIsAlreadyRead() throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/notifications/mine/read").header("X-User-Id", "42").param("throughId", "103"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$").value(false));
        }
        verify(repository, times(2)).markReadThroughForUser(42L, 103L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc"})
    void rejectsInvalidReadBoundary(String throughId) throws Exception {
        mvc.perform(post("/notifications/mine/read").header("X-User-Id", "42").param("throughId", throughId))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }

    @Test
    void readEndpointsRequireValidClient() throws Exception {
        mvc.perform(get("/notifications/mine/unread")).andExpect(status().isBadRequest());
        mvc.perform(get("/notifications/mine/unread").header("X-User-Id", "0")).andExpect(status().isBadRequest());
        mvc.perform(post("/notifications/mine/read").header("X-User-Id", "-1").param("throughId", "103"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }

    private Notification notification(long id, NotificationType type) {
        return Notification.builder().notificationId(id).userId(42L).shipmentId(15L)
                .type(type).message(type.message()).createdAt(LocalDateTime.of(2026, 11, 19, 10, (int) id)).build();
    }
}
