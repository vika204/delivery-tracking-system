package com.delivery.webui.client.dto;


import java.math.BigDecimal;

public record ShipmentQuoteRequest(
        String pickupAddress,
        String pickupCityCode,
        String deliveryAddress,
        String deliveryCityCode,
        BigDecimal weight,
        String deliveryType
) {
}
