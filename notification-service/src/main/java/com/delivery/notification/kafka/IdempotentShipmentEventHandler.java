package com.delivery.notification.kafka;

import com.delivery.notification.entity.Notification;
import com.delivery.notification.entity.NotificationType;
import com.delivery.notification.entity.ProcessedEvent;
import com.delivery.notification.event.ShipmentCreatedEvent;
import com.delivery.notification.event.ShipmentStatusChangedEvent;
import com.delivery.notification.repository.NotificationRepository;
import com.delivery.notification.repository.ProcessedEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotentShipmentEventHandler implements ShipmentEventHandler {

    private final ProcessedEventRepository processedEventRepository;
    private final NotificationRepository notificationRepository;

    @Override
    @Transactional
    public void handle(ShipmentCreatedEvent event) {
        save(event.eventId(), event.payload().userId(), event.payload().shipmentId(), NotificationType.SHIPMENT_CREATED,
                "your shipment #" + event.payload().shipmentId() + " has been created with status: " + event.payload().status());
    }

    @Transactional
    public void handle(ShipmentStatusChangedEvent event) {
        NotificationType type = NotificationType.fromShipmentStatus(event.status());
        if (type != null) {
            save(event.eventId(), event.senderId(), event.shipmentId(), type, type.message());
        }
    }

    private void save(UUID eventId, long userId, long shipmentId, NotificationType type, String message) {
        if (processedEventRepository.existsById(eventId)) {
            log.info("Notification event already processed: eventId={}", eventId);
            return;
        }

        Notification notification = Notification.builder()
                .userId(userId)
                .shipmentId(shipmentId)
                .type(type)
                .message(message)
                .isRead(false)
                .build();
        notificationRepository.save(notification);

        processedEventRepository.save(new ProcessedEvent(eventId, Instant.now()));

        log.info("Notification saved: eventId={}, shipmentId={}, type={}", eventId, shipmentId, type);
    }
}
