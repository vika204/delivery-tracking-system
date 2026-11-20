package com.delivery.tracking.client.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateShipmentRequest(
        UUID quoteId,
        String senderName,
        String senderPhone,
        String recipientName,
        String recipientPhone,
        BigDecimal length,
        BigDecimal width,
        BigDecimal height
) {
}
