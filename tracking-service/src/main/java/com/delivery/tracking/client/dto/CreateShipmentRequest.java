package com.delivery.tracking.client.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Контракт shipment-service POST /shipments: доставка створюється за раніше
 * розрахованою пропозицією ціни (quoteId); маршрут, вага й ціна беруться з неї.
 */
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
