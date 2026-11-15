package com.delivery.shipment.pricing;

import com.delivery.shipment.entity.DeliveryType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class PriceCalculator {

    private final TariffProperties tariff;

    public PriceCalculator(TariffProperties tariff) {
        this.tariff = tariff;
    }

    public PriceBreakdown calculate(BigDecimal distanceKm, BigDecimal weightKg, DeliveryType deliveryType) {
        BigDecimal distanceCharge = money(distanceKm.multiply(tariff.pricePerKm()));
        BigDecimal weightCharge = money(weightKg.multiply(tariff.pricePerKg()));
        BigDecimal subtotal = money(tariff.basePrice().add(distanceCharge).add(weightCharge));
        BigDecimal multiplier = multiplier(deliveryType);

        return new PriceBreakdown(
                money(tariff.basePrice()),
                distanceKm,
                tariff.pricePerKm(),
                distanceCharge,
                weightKg,
                tariff.pricePerKg(),
                weightCharge,
                subtotal,
                deliveryType,
                multiplier,
                money(subtotal.multiply(multiplier))
        );
    }

    private BigDecimal multiplier(DeliveryType deliveryType) {
        return deliveryType == DeliveryType.EXPRESS ? tariff.expressMultiplier() : BigDecimal.ONE;
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
