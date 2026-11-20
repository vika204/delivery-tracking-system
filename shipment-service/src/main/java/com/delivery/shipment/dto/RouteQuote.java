package com.delivery.shipment.dto;

import com.delivery.shipment.geo.Coordinates;

import java.math.BigDecimal;

public record RouteQuote(
        Coordinates pickup,
        Coordinates delivery,
        BigDecimal distanceKm,
        PriceBreakdown breakdown
) {
}
