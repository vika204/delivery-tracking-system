package com.delivery.shipment.dto;


import java.math.BigDecimal;

public record ShipmentUpdateResponse(
        ShipmentResponse shipment,
        boolean priceRecalculated,
        BigDecimal previousPrice,
        PriceBreakdown breakdown
) {
}
