package com.delivery.webui.client.dto;

import java.time.LocalDateTime;

public record NotificationResponse(
        long notificationId,
        long shipmentId,
        String type,
        String message,
        LocalDateTime createdAt,
        boolean read
) {
}
