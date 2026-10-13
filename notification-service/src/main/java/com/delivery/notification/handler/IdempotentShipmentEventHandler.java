package com.delivery.notification.handler;

import com.delivery.events.ShipmentCreatedEvent;
import com.delivery.notification.entity.Notification;
import com.delivery.notification.entity.NotificationType;
import com.delivery.notification.entity.ProcessedEvent;
import com.delivery.notification.repository.NotificationRepository;
import com.delivery.notification.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotentShipmentEventHandler implements ShipmentEventHandler {

    private final ProcessedEventRepository processedEventRepository;
    private final NotificationRepository notificationRepository;

    @Override
    @Transactional
    public void handle(ShipmentCreatedEvent event) {
        if (processedEventRepository.existsById(event.eventId())) {
            log.warn("event {} was already processed — skipping duplicate", event.eventId());
            return;
        }

        Notification notification = Notification.builder()
                .userId(event.payload().userId())
                .shipmentId(event.payload().shipmentId())
                .type(NotificationType.SHIPMENT_CREATED)
                .message("your shipment #" + event.payload().shipmentId()
                         + " has been created with status: " + event.payload().status())
                .isRead(false)
                .build();
        notificationRepository.save(notification);

        processedEventRepository.save(new ProcessedEvent(event.eventId(), Instant.now()));

        log.info("processed shipment event {} for shipment #{}", event.eventId(), event.payload().shipmentId());
    }
}
