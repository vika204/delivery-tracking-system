package com.delivery.shipment.pricing;

import com.delivery.shipment.geo.Coordinates;

import java.math.BigDecimal;

/** Результат геокодування маршруту й розрахунку ціни. */
public record RouteQuote(
        Coordinates pickup,
        Coordinates delivery,
        BigDecimal distanceKm,
        PriceBreakdown breakdown
) {
}
