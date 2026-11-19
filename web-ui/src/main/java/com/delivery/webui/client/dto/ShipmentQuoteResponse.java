package com.delivery.webui.client.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ShipmentQuoteResponse(
        UUID quoteId,
        Instant expiresAt,
        BigDecimal distanceKm,
        BigDecimal totalPrice
) {
}
