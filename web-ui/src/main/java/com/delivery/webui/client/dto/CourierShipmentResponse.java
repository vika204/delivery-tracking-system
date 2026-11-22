package com.delivery.webui.client.dto;

import java.math.BigDecimal;

public record CourierShipmentResponse(
        long shipmentId,
        String status,
        String pickupAddress,
        String deliveryAddress,
        Contact recipient,
        BigDecimal weight,
        BigDecimal length,
        BigDecimal width,
        BigDecimal height,
        BigDecimal distance,
        BigDecimal price
) {

    public record Contact(String initials, String phone) {
    }

}
