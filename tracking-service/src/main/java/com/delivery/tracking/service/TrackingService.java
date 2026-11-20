package com.delivery.tracking.service;

import com.delivery.tracking.client.ShipmentClient;
import com.delivery.tracking.client.dto.CreateShipmentRequest;
import com.delivery.tracking.client.dto.ShipmentBatchResponse;
import com.delivery.tracking.client.dto.ShipmentDto;
import com.delivery.tracking.metrics.TrackingMetrics;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;

@Service
public class TrackingService {

    private final ShipmentClient shipmentClient;
    private final TrackingMetrics metrics;

    public TrackingService(ShipmentClient shipmentClient, TrackingMetrics metrics) {
        this.shipmentClient = shipmentClient;
        this.metrics = metrics;
    }

    @Bulkhead(name = "shipmentClient")
    @CircuitBreaker(
            name = "shipmentClient",
            fallbackMethod = "getShipmentFallback"
    )
    @Retry(name = "shipmentClient")
    public ShipmentDto getShipmentById(Long shipmentId) {
        return metrics.timeShipmentCall("get", () -> shipmentClient.getShipmentById(shipmentId));
    }

    @Bulkhead(name = "shipmentClient")
    @CircuitBreaker(name = "shipmentClient")
    @Retry(name = "shipmentClient")
    public ShipmentDto getShipmentByIdWithoutFallback(Long shipmentId) {
        return metrics.timeShipmentCall("get", () -> shipmentClient.getShipmentById(shipmentId));
    }

    @Bulkhead(name = "shipmentClient")
    @CircuitBreaker(name = "shipmentClient")
    @Retry(name = "shipmentClient")
    public ShipmentBatchResponse getShipmentsBatch(List<Long> ids) {
        metrics.batchRequested(ids.size());
        return metrics.timeShipmentCall("batch", () -> shipmentClient.getShipmentsBatch(ids));
    }

    @Bulkhead(name = "shipmentClient")
    @CircuitBreaker(name = "shipmentClient")
    @Retry(name = "shipmentClient")
    public ShipmentDto createShipment(
            String idempotencyKey,
            Long userId,
            CreateShipmentRequest request
    ) {
        return metrics.timeShipmentCall("create", () -> shipmentClient.createShipment(idempotencyKey, userId, request));
    }

    private ShipmentDto getShipmentFallback(
            Long shipmentId,
            CallNotPermittedException ex
    ) {
        return unavailableShipment(shipmentId);
    }

    private ShipmentDto getShipmentFallback(
            Long shipmentId,
            ResourceAccessException ex
    ) {
        return unavailableShipment(shipmentId);
    }

    private ShipmentDto getShipmentFallback(
            Long shipmentId,
            HttpServerErrorException ex
    ) {
        return unavailableShipment(shipmentId);
    }

    private ShipmentDto unavailableShipment(Long shipmentId) {
        metrics.fallbackUsed();
        return new ShipmentDto(
                shipmentId,
                "Дані тимчасово недоступні (Fallback)",
                "UNAVAILABLE"
        );
    }
}