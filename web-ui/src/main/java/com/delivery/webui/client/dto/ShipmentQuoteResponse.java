package com.delivery.webui.client.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ShipmentQuoteResponse(
        UUID quoteId,
        BigDecimal distanceKm,
        BigDecimal totalPrice
) {
}
