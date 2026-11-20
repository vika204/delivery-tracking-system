package com.delivery.shipment.service;

import com.delivery.shipment.config.TariffProperties;
import com.delivery.shipment.dto.PriceBreakdown;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class PriceCalculatorTest {

    private final PriceCalculator calculator = new PriceCalculator(new TariffProperties(
            new BigDecimal("50.00"), new BigDecimal("1.50"), new BigDecimal("10.00"),
            Duration.ofMinutes(15)));

    @Test
    void priceIsBasePlusDistancePlusWeight() {
        PriceBreakdown price = calculator.calculate(new BigDecimal("100.0"), new BigDecimal("2.5"));

        assertThat(price.basePrice()).isEqualByComparingTo("50.00");
        assertThat(price.distanceCharge()).isEqualByComparingTo("150.00");
        assertThat(price.weightCharge()).isEqualByComparingTo("25.00");
        assertThat(price.total()).isEqualByComparingTo("225.00");
    }

    @Test
    void zeroDistanceChargesOnlyBaseAndWeight() {
        PriceBreakdown price = calculator.calculate(BigDecimal.ZERO, BigDecimal.ONE);

        assertThat(price.distanceCharge()).isEqualByComparingTo("0.00");
        assertThat(price.total()).isEqualByComparingTo("60.00");
    }

    @Test
    void amountsAreRoundedToKopecks() {
        PriceBreakdown price = calculator.calculate(new BigDecimal("3.3"), new BigDecimal("0.333"));

        assertThat(price.distanceCharge()).isEqualByComparingTo("4.95");
        assertThat(price.weightCharge()).isEqualByComparingTo("3.33");
        assertThat(price.total().scale()).isEqualTo(2);
    }
}
