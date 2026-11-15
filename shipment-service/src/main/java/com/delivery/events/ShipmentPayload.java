package com.delivery.events;

import java.math.BigDecimal;

/**
 * Snapshot доставки для інших контекстів. Перші три поля — початковий контракт
 * (їх читає notification-service); решта потрібна Dispatch для DispatchRequest.
 */
public record ShipmentPayload(
        Long shipmentId,
        Long userId,
        String status,
        String pickupAddress,
        String pickupCityCode,
        Double pickupLatitude,
        Double pickupLongitude,
        String deliveryAddress,
        String deliveryCityCode,
        Double deliveryLatitude,
        Double deliveryLongitude,
        BigDecimal weight,
        BigDecimal distance,
        BigDecimal price,
        String deliveryType
) {
}
