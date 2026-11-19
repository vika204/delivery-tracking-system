package com.delivery.webui.client.dto;


import java.math.BigDecimal;
import java.util.UUID;

/** POST /shipments: маршрут, вага й ціна беруться з пропозиції quoteId. */
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
