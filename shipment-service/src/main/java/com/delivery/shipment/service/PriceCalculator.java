package com.delivery.shipment.service;

import com.delivery.shipment.config.TariffProperties;
import com.delivery.shipment.dto.PriceBreakdown;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class PriceCalculator {

    private final TariffProperties tariff;

    public PriceCalculator(TariffProperties tariff) {
        this.tariff = tariff;
    }

    public PriceBreakdown calculate(BigDecimal distanceKm, BigDecimal weightKg) {
        BigDecimal distanceCharge = money(distanceKm.multiply(tariff.pricePerKm()));
        BigDecimal weightCharge = money(weightKg.multiply(tariff.pricePerKg()));
        BigDecimal total = money(tariff.basePrice().add(distanceCharge).add(weightCharge));

        return new PriceBreakdown(
                money(tariff.basePrice()),
                distanceKm,
                tariff.pricePerKm(),
                distanceCharge,
                weightKg,
                tariff.pricePerKg(),
                weightCharge,
                total
        );
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
