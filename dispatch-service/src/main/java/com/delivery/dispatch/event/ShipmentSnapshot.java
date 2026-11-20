package com.delivery.dispatch.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ShipmentSnapshot(
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
        BigDecimal price
) {
}
