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

    //тут фолбек приписуємо, бо якщо shipment-service недоступний,
    //то можемо просто повернути і сказати однозначно, що дані зараз недоступні
    @Bulkhead(name = "shipmentClient")
    @CircuitBreaker(
            name = "shipmentClient",
            fallbackMethod = "getShipmentFallback"
    )
    @Retry(name = "shipmentClient")
    public ShipmentDto getShipmentById(Long shipmentId) {
        return metrics.timeShipmentCall("get", () -> shipmentClient.getShipmentById(shipmentId));
    }

    //для batch фолбек не прописуємо, бо порожній або неповний результат
    //можна сприйняти так, ніби нічого не знайдено, хоча сервіс просто недоступний
    @Bulkhead(name = "shipmentClient")
    @CircuitBreaker(name = "shipmentClient")
    @Retry(name = "shipmentClient")
    public ShipmentBatchResponse getShipmentsBatch(List<Long> ids) {
        metrics.batchRequested(ids.size());
        return metrics.timeShipmentCall("batch", () -> shipmentClient.getShipmentsBatch(ids));
    }

    //при створенні ми не знаємо, чи посилка не створилась,
    //чи вона створилась, але відповідь від shipment-service не встигла прийти,
    //тому нормальний фолбек тут однозначно повернути не можемо
    @Bulkhead(name = "shipmentClient")
    @CircuitBreaker(name = "shipmentClient")
    @Retry(name = "shipmentClient")
    public ShipmentDto createShipment(
            String idempotencyKey,
            CreateShipmentRequest request
    ) {
        return metrics.timeShipmentCall("create", () -> shipmentClient.createShipment(idempotencyKey, request));
    }

    //робимо окремі фолбеки тільки для технічних помилок,
    //бо якщо поставити загальний Throwable, сюди може потрапити і 404,
    //а її треба повернути як звичайну помилку через ProblemDetail
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