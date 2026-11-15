package com.delivery.shipment.pricing;

import com.delivery.shipment.entity.DeliveryType;
import com.delivery.shipment.geo.Coordinates;
import com.delivery.shipment.geo.RoutingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Pricing Context. Знаходиться в shipment-service окремим пакетом і спілкується з рештою
 * коду лише через цей сервіс, тож його можна винести в окремий мікросервіс без змін у Shipment.
 */
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

    /** Геокодує адреси, рахує відстань і ціну. Нічого не зберігає. */
    public RouteQuote calculate(
            String pickupCityCode,
            String pickupAddress,
            String deliveryCityCode,
            String deliveryAddress,
            BigDecimal weight,
            DeliveryType deliveryType
    ) {
        Coordinates pickup = routingClient.geocode(pickupCityCode, pickupAddress);
        Coordinates delivery = routingClient.geocode(deliveryCityCode, deliveryAddress);
        BigDecimal distanceKm = BigDecimal.valueOf(routingClient.routeDistanceKm(pickup, delivery))
                .setScale(1, RoundingMode.HALF_UP);

        return new RouteQuote(pickup, delivery, distanceKm, calculator.calculate(distanceKm, weight, deliveryType));
    }

    @Transactional
    public PriceQuote createQuote(
            long userId,
            String pickupCityCode,
            String pickupAddress,
            String deliveryCityCode,
            String deliveryAddress,
            BigDecimal weight,
            DeliveryType deliveryType
    ) {
        RouteQuote route = calculate(pickupCityCode, pickupAddress, deliveryCityCode, deliveryAddress, weight, deliveryType);
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
                .deliveryType(deliveryType)
                .basePrice(price.basePrice())
                .pricePerKm(price.pricePerKm())
                .pricePerKg(price.pricePerKg())
                .distanceCharge(price.distanceCharge())
                .weightCharge(price.weightCharge())
                .deliveryTypeMultiplier(price.deliveryTypeMultiplier())
                .totalPrice(price.total())
                .calculatedAt(now)
                .expiresAt(now.plus(tariff.quoteTtl()))
                .build());

        log.info("Price quote calculated: quoteId={}, userId={}, distanceKm={}, weight={}, type={}, total={}",
                quote.getQuoteId(), userId, route.distanceKm(), weight, deliveryType, price.total());
        return quote;
    }

    /** Блокує та перевіряє пропозицію перед створенням доставки. */
    @Transactional
    public PriceQuote lockQuote(UUID quoteId, long userId) {
        PriceQuote quote = quoteRepository.findByIdForUpdate(quoteId)
                // Чужа пропозиція виглядає як неіснуюча, щоб не розкривати її наявність.
                .filter(found -> found.getUserId() == userId)
                .orElseThrow(() -> new QuoteException(QuoteException.Reason.NOT_FOUND, quoteId));

        if (quote.getShipmentId() != null) {
            throw new QuoteException(QuoteException.Reason.ALREADY_USED, quoteId);
        }
        if (!quote.getExpiresAt().isAfter(Instant.now(clock))) {
            throw new QuoteException(QuoteException.Reason.EXPIRED, quoteId);
        }
        return quote;
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
                quote.getBasePrice().add(quote.getDistanceCharge()).add(quote.getWeightCharge()),
                quote.getDeliveryType(),
                quote.getDeliveryTypeMultiplier(),
                quote.getTotalPrice()
        );
    }
}
