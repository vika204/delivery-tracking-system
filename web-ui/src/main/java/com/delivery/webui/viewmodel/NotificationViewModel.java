package com.delivery.webui.viewmodel;

import com.delivery.webui.client.dto.NotificationResponse;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public record NotificationViewModel(long shipmentId, String eventTitle, String message, LocalDateTime createdAt, boolean read) {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH);

    public static NotificationViewModel from(NotificationResponse notification) {
        return new NotificationViewModel(notification.shipmentId(), notification.type().replace('_', ' '),
                notification.message(), notification.createdAt(), notification.read());
    }

    public String dateTimeText() {
        return DATE_TIME.format(createdAt);
    }
}
