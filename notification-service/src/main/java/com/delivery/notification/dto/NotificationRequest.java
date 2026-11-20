package com.delivery.notification.dto;

import com.delivery.notification.entity.NotificationType;

public record NotificationRequest(
        Long userId,
        Long shipmentId,
        NotificationType type,
        String message,
        boolean isRead
) {
}
