package com.delivery.shipment.service;

import com.delivery.shipment.config.TariffProperties;
import com.delivery.shipment.dto.PriceBreakdown;
import com.delivery.shipment.dto.RouteQuote;
import com.delivery.shipment.entity.PriceQuote;
import com.delivery.shipment.exception.QuoteException;
import com.delivery.shipment.geo.Coordinates;
import com.delivery.shipment.geo.RoutingClient;
import com.delivery.shipment.repository.PriceQuoteRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class PricingService {

    private static final Logger log = LoggerFactory.getLogger(PricingService.class);

    private final RoutingClient routingClient;
    private final PriceCalculator calculator;
    private final PriceQuoteRepository quoteRepository;
    private final TariffProperties tariff;
    private final Clock clock;

    public PricingService(
            RoutingClient routingClient,
            PriceCalculator calculator,
            PriceQuoteRepository quoteRepository,
            TariffProperties tariff,
            Clock clock
    ) {
        this.routingClient = routingClient;
        this.calculator = calculator;
        this.quoteRepository = quoteRepository;
        this.tariff = tariff;
        this.clock = clock;
    }

    public RouteQuote calculate(
            String pickupCityCode,
            String pickupAddress,
            String deliveryCityCode,
            String deliveryAddress,
            BigDecimal weight
    ) {
        Coordinates pickup = routingClient.geocode(pickupCityCode, pickupAddress);
        Coordinates delivery = routingClient.geocode(deliveryCityCode, deliveryAddress);
        BigDecimal distanceKm = BigDecimal.valueOf(routingClient.routeDistanceKm(pickup, delivery))
                .setScale(2, RoundingMode.HALF_UP);

        return new RouteQuote(pickup, delivery, distanceKm, calculator.calculate(distanceKm, weight));
    }

    @Transactional
    public PriceQuote createQuote(
            long userId,
            String pickupCityCode,
            String pickupAddress,
            String deliveryCityCode,
            String deliveryAddress,
            BigDecimal weight
    ) {
        RouteQuote route = calculate(pickupCityCode, pickupAddress, deliveryCityCode, deliveryAddress, weight);
        PriceBreakdown price = route.breakdown();
        Instant now = Instant.now(clock);

        PriceQuote quote = quoteRepository.save(PriceQuote.builder()
                .quoteId(UUID.randomUUID())
                .userId(userId)
                .pickupAddress(pickupAddress.trim())
                .pickupCityCode(pickupCityCode)
                .pickupLatitude(route.pickup().latitude())
                .pickupLongitude(route.pickup().longitude())
                .deliveryAddress(deliveryAddress.trim())
                .deliveryCityCode(deliveryCityCode)
                .deliveryLatitude(route.delivery().latitude())
                .deliveryLongitude(route.delivery().longitude())
                .distanceKm(route.distanceKm())
                .weight(weight)
                .basePrice(price.basePrice())
                .pricePerKm(price.pricePerKm())
                .pricePerKg(price.pricePerKg())
                .distanceCharge(price.distanceCharge())
                .weightCharge(price.weightCharge())
                .totalPrice(price.total())
                .calculatedAt(now)
                .expiresAt(now.plus(tariff.quoteTtl()))
                .build());

        log.info("Price quote calculated: quoteId={}, userId={}, distanceKm={}, weight={}, total={}",
                quote.getQuoteId(), userId, route.distanceKm(), weight, price.total());
        return quote;
    }

    @Transactional
    public PriceQuote lockQuote(UUID quoteId, long userId) {
        return quoteRepository.findByIdForUpdate(quoteId)
                .filter(found -> found.getUserId() == userId)
                .orElseThrow(() -> new QuoteException(QuoteException.Reason.NOT_FOUND, quoteId));
    }

    public void requireUsable(PriceQuote quote) {
        if (quote.getShipmentId() != null) {
            throw new QuoteException(QuoteException.Reason.ALREADY_USED, quote.getQuoteId());
        }
        if (!quote.getExpiresAt().isAfter(Instant.now(clock))) {
            throw new QuoteException(QuoteException.Reason.EXPIRED, quote.getQuoteId());
        }
    }

    public static PriceBreakdown breakdownOf(PriceQuote quote) {
        return new PriceBreakdown(
                quote.getBasePrice(),
                quote.getDistanceKm(),
                quote.getPricePerKm(),
                quote.getDistanceCharge(),
                quote.getWeight(),
                quote.getPricePerKg(),
                quote.getWeightCharge(),
                quote.getTotalPrice()
        );
    }
}
