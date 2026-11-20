package com.delivery.notification.kafka;

import com.delivery.notification.entity.Notification;
import com.delivery.notification.entity.NotificationType;
import com.delivery.notification.event.ShipmentCreatedEvent;
import com.delivery.notification.event.ShipmentPayload;
import com.delivery.notification.event.ShipmentStatusChangedEvent;
import com.delivery.notification.repository.NotificationRepository;
import com.delivery.notification.repository.ProcessedEventRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IdempotentShipmentEventHandlerTest {

    private final ProcessedEventRepository processed = mock(ProcessedEventRepository.class);
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final IdempotentShipmentEventHandler handler = new IdempotentShipmentEventHandler(processed, notifications);

    @ParameterizedTest
    @CsvSource({"ASSIGNED,COURIER_ASSIGNED", "IN_TRANSIT,PACKAGE_PICKED_UP",
            "DELIVERED,DELIVERED", "CANCELLED,SHIPMENT_CANCELLED", "WAITING_FOR_COURIER,COURIER_REASSIGNMENT"})
    void createsStatusNotificationForEventSender(String status, NotificationType expected) {
        ShipmentStatusChangedEvent event = event(status);

        handler.handle(event);

        var saved = org.mockito.ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(42L);
        assertThat(saved.getValue().getShipmentId()).isEqualTo(15L);
        assertThat(saved.getValue().getType()).isEqualTo(expected);
        assertThat(saved.getValue().getMessage()).isEqualTo(expected.message());
        verify(processed).save(any());
    }

    @Test
    void repeatedEventDoesNotCreateDuplicateNotification() {
        ShipmentStatusChangedEvent event = event("DELIVERED");
        when(processed.existsById(event.eventId())).thenReturn(false, true);

        handler.handle(event);
        handler.handle(event);

        verify(notifications, times(1)).save(any());
        verify(processed, times(1)).save(any());
    }

    @Test
    void doesNotMarkEventProcessedWhenSavingNotificationFails() {
        when(notifications.save(any())).thenThrow(new DataAccessResourceFailureException("Database unavailable"));

        assertThatThrownBy(() -> handler.handle(event("DELIVERED"))).isInstanceOf(DataAccessResourceFailureException.class);
        verify(processed, never()).save(any());
    }

    @Test
    void keepsExistingShipmentCreatedBehavior() {
        handler.handle(new ShipmentCreatedEvent(UUID.randomUUID(), ShipmentCreatedEvent.TYPE, Instant.now(),
                new ShipmentPayload(15L, 42L, "WAITING_FOR_COURIER")));

        var saved = org.mockito.ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo(NotificationType.SHIPMENT_CREATED);
        assertThat(saved.getValue().getMessage()).isEqualTo("your shipment #15 has been created with status: WAITING_FOR_COURIER");
    }

    @Test
    void ignoresUnknownStatusWithoutInventingNotification() {
        handler.handle(event("UNKNOWN_STATUS"));
        verifyNoInteractions(processed, notifications);
    }

    private ShipmentStatusChangedEvent event(String status) {
        return new ShipmentStatusChangedEvent(UUID.randomUUID(), ShipmentStatusChangedEvent.TYPE,
                Instant.now(), 15L, status, 42L);
    }
}
