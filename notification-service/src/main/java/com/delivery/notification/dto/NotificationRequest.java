package com.delivery.notification.dto;

import com.delivery.notification.entity.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record NotificationRequest(
        @NotNull @Positive Long userId,
        @NotNull @Positive Long shipmentId,
        @NotNull NotificationType type,
        @NotBlank @Size(max = 255) String message,
        boolean isRead
) {
}
