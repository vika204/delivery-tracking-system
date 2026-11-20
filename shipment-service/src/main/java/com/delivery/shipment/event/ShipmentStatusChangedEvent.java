package com.delivery.shipment.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Зміна статусу доставки. Перші п'ять полів — початковий контракт (його читає tracking-service).
 * Решта додана в кінець, тож наявні споживачі їх ігнорують:
 * <ul>
 *   <li>previousStatus, courierId, actorType, reason — Dispatch і таймлайн;</li>
 *   <li>senderId, courierName — notification-service може сповістити відправника й кур'єра без запиту до Shipment;</li>
 *   <li>координати забору й доставки — Tracking створює TrackingSession і рахує ETA.</li>
 * </ul>
 */
public record ShipmentStatusChangedEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        Long shipmentId,
        String status,
        String previousStatus,
        Long courierId,
        String actorType,
        String reason,
        Long senderId,
        String courierName,
        Double pickupLatitude,
        Double pickupLongitude,
        Double deliveryLatitude,
        Double deliveryLongitude
) {
    public static final String TYPE = "SHIPMENT_STATUS_CHANGED";
}
