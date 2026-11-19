package com.delivery.webui.client.dto;

import java.math.BigDecimal;

/** GET /shipments/{id}/courier-view: те, що бачить призначений кур'єр. */
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
        BigDecimal price,
        String deliveryType
) {

    public record Contact(String initials, String phone) {
    }

    public boolean inTransit() {
        return "IN_TRANSIT".equals(status);
    }
}
