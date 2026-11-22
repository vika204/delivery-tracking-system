package com.delivery.shipment.dto;

import com.delivery.shipment.entity.PriceQuote;
import com.delivery.shipment.service.PricingService;

import java.math.BigDecimal;
import java.util.UUID;

public record QuoteResponse(
        UUID quoteId,
        Point pickup,
        Point delivery,
        BigDecimal distanceKm,
        BigDecimal weight,
        PriceBreakdown breakdown,
        BigDecimal totalPrice
) {

    public record Point(String address, String cityCode, double latitude, double longitude) {
    }

    public static QuoteResponse from(PriceQuote quote) {
        return new QuoteResponse(
                quote.getQuoteId(),
                new Point(quote.getPickupAddress(), quote.getPickupCityCode(),
                        quote.getPickupLatitude(), quote.getPickupLongitude()),
                new Point(quote.getDeliveryAddress(), quote.getDeliveryCityCode(),
                        quote.getDeliveryLatitude(), quote.getDeliveryLongitude()),
                quote.getDistanceKm(),
                quote.getWeight(),
                PricingService.breakdownOf(quote),
                quote.getTotalPrice()
        );
    }
}
