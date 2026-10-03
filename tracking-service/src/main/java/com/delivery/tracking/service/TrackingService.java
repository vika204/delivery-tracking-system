package com.delivery.tracking.service;

import com.delivery.tracking.client.ShipmentClient;
import com.delivery.tracking.client.dto.ShipmentDto;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Service;

@Service
public class TrackingService {

    private final ShipmentClient shipmentClient;

    public TrackingService(ShipmentClient shipmentClient) {
        this.shipmentClient = shipmentClient;
    }

    @Bulkhead(name = "shipmentClient")
    @CircuitBreaker(
            name = "shipmentClient",
            fallbackMethod = "getShipmentFallback"
    )
    @Retry(name = "shipmentClient")
    public ShipmentDto getShipmentById(Long shipmentId) {
        return shipmentClient.getShipmentById(shipmentId);
    }

    public ShipmentDto getShipmentFallback(Long shipmentId, Throwable ex) {
        return new ShipmentDto(
                shipmentId,
                null,
                "Дані тимчасово недоступні (Fallback)",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "UNAVAILABLE",
                null
        );
    }

}