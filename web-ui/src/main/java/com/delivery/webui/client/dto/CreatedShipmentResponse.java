package com.delivery.webui.client.dto;

import java.math.BigDecimal;

public record CreatedShipmentResponse(
        long shipmentId,
        String status,
        BigDecimal price
) {
}
