package com.delivery.notification.dto;

import com.delivery.notification.entity.Notification;
import com.delivery.notification.entity.NotificationType;

import java.time.LocalDateTime;

public record NotificationResponse(
        long notificationId,
        long shipmentId,
        NotificationType type,
        String message,
        LocalDateTime createdAt,
        boolean read
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.getNotificationId(), notification.getShipmentId(),
                notification.getType(), notification.getMessage(), notification.getCreatedAt(), notification.isRead());
    }
}
