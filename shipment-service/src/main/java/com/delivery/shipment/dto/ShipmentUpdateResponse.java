package com.delivery.shipment.dto;


import java.math.BigDecimal;

/** Результат редагування; breakdown є лише тоді, коли ціну перераховано (US-2.4). */
public record ShipmentUpdateResponse(
        ShipmentResponse shipment,
        boolean priceRecalculated,
        BigDecimal previousPrice,
        PriceBreakdown breakdown
) {
}
