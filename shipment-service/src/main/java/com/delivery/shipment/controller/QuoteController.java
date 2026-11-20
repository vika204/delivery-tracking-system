package com.delivery.shipment.controller;

import com.delivery.shipment.controller.support.RequestHeaders;
import com.delivery.shipment.dto.QuoteRequest;
import com.delivery.shipment.dto.QuoteResponse;
import com.delivery.shipment.entity.PriceQuote;
import com.delivery.shipment.service.PricingService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/shipments/quotes")
public class QuoteController {

    private final PricingService pricingService;

    public QuoteController(PricingService pricingService) {
        this.pricingService = pricingService;
    }

    @PostMapping
    public ResponseEntity<QuoteResponse> createQuote(
            @RequestHeader(RequestHeaders.USER_ID) @Positive long userId,
            @Valid @RequestBody QuoteRequest request
    ) {
        PriceQuote quote = pricingService.createQuote(
                userId,
                request.pickupCityCode(),
                request.pickupAddress(),
                request.deliveryCityCode(),
                request.deliveryAddress(),
                request.weight(),
                request.deliveryTypeOrDefault()
        );
        return ResponseEntity.status(201).body(QuoteResponse.from(quote));
    }
}
