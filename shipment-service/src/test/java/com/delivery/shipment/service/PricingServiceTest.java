package com.delivery.shipment.service;

import com.delivery.shipment.config.TariffProperties;
import com.delivery.shipment.dto.QuoteResponse;
import com.delivery.shipment.entity.PriceQuote;
import com.delivery.shipment.geo.Coordinates;
import com.delivery.shipment.geo.RoutingClient;
import com.delivery.shipment.repository.PriceQuoteRepository;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PricingServiceTest {

    @ParameterizedTest
    @CsvSource({
            "0.02, 0.02, 0.03, 60.03",
            "1.234, 1.23, 1.85, 61.85"
    })
    void savesDistanceWithDatabasePrecisionAndUsesItForPricing(
            double routeKm, String expectedKm, String expectedCharge, String expectedTotal
    ) {
        RoutingClient routing = mock(RoutingClient.class);
        PriceQuoteRepository repository = mock(PriceQuoteRepository.class);
        TariffProperties tariff = new TariffProperties(
                new BigDecimal("50.00"), new BigDecimal("1.50"), new BigDecimal("10.00"));
        PricingService service = new PricingService(routing, new PriceCalculator(tariff),
                repository, Clock.fixed(Instant.parse("2026-11-19T17:00:00Z"), ZoneOffset.UTC));
        String cityCode = "UA80000000000093317";
        String pickupAddress = "Київ, Іллінська, 2";
        String deliveryAddress = "Київ, Контрактова площа, 2";
        Coordinates pickup = new Coordinates(50.4638728, 30.5203789);
        Coordinates delivery = new Coordinates(50.4635888, 30.5202942);
        when(routing.geocode(cityCode, pickupAddress)).thenReturn(pickup);
        when(routing.geocode(cityCode, deliveryAddress)).thenReturn(delivery);
        when(routing.routeDistanceKm(pickup, delivery)).thenReturn(routeKm);
        when(repository.save(any(PriceQuote.class))).thenAnswer(call -> call.getArgument(0));

        PriceQuote quote = service.createQuote(42, cityCode, pickupAddress, cityCode, deliveryAddress,
                BigDecimal.ONE);

        verify(repository).save(quote);
        assertThat(quote.getDistanceKm()).isEqualByComparingTo(expectedKm);
        assertThat(quote.getDistanceKm().scale()).isEqualTo(2);
        assertThat(quote.getDistanceCharge()).isEqualByComparingTo(expectedCharge);
        assertThat(quote.getTotalPrice()).isEqualByComparingTo(expectedTotal);
        assertThat(QuoteResponse.from(quote).distanceKm()).isEqualByComparingTo(expectedKm);
    }
}
