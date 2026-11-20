package com.delivery.notification.dto;

import com.delivery.notification.entity.Notification;
import com.delivery.notification.entity.NotificationType;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long notificationId,
        Long userId,
        Long shipmentId,
        NotificationType type,
        String message,
        boolean isRead,
        LocalDateTime createdAt
) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getNotificationId(),
                n.getUserId(),
                n.getShipmentId(),
                n.getType(),
                n.getMessage(),
                n.isRead(),
                n.getCreatedAt());
    }
}
