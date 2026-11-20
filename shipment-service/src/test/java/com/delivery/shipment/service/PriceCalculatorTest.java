package com.delivery.shipment.service;

import com.delivery.shipment.config.TariffProperties;
import com.delivery.shipment.dto.PriceBreakdown;
import com.delivery.shipment.entity.DeliveryType;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class PriceCalculatorTest {

    private final PriceCalculator calculator = new PriceCalculator(new TariffProperties(
            new BigDecimal("50.00"), new BigDecimal("1.50"), new BigDecimal("10.00"),
            new BigDecimal("1.5"), Duration.ofMinutes(15)));

    @Test
    void standardPriceIsBasePlusDistancePlusWeight() {
        PriceBreakdown price = calculator.calculate(new BigDecimal("100.0"), new BigDecimal("2.5"), DeliveryType.STANDARD);

        assertThat(price.basePrice()).isEqualByComparingTo("50.00");
        assertThat(price.distanceCharge()).isEqualByComparingTo("150.00");
        assertThat(price.weightCharge()).isEqualByComparingTo("25.00");
        assertThat(price.subtotal()).isEqualByComparingTo("225.00");
        assertThat(price.deliveryTypeMultiplier()).isEqualByComparingTo("1");
        assertThat(price.total()).isEqualByComparingTo("225.00");
    }

    @Test
    void expressAppliesMultiplierToSubtotal() {
        PriceBreakdown price = calculator.calculate(new BigDecimal("100.0"), new BigDecimal("2.5"), DeliveryType.EXPRESS);

        assertThat(price.subtotal()).isEqualByComparingTo("225.00");
        assertThat(price.deliveryTypeMultiplier()).isEqualByComparingTo("1.5");
        assertThat(price.total()).isEqualByComparingTo("337.50");
    }

    @Test
    void amountsAreRoundedToKopecks() {
        PriceBreakdown price = calculator.calculate(new BigDecimal("3.3"), new BigDecimal("0.333"), DeliveryType.STANDARD);

        assertThat(price.distanceCharge()).isEqualByComparingTo("4.95");
        assertThat(price.weightCharge()).isEqualByComparingTo("3.33");
        assertThat(price.total().scale()).isEqualTo(2);
    }
}
