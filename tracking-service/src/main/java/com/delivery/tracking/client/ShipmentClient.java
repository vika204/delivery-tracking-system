package com.delivery.tracking.client;

import com.delivery.tracking.client.dto.CreateShipmentRequest;
import com.delivery.tracking.client.dto.ShipmentBatchResponse;
import com.delivery.tracking.client.dto.ShipmentDto;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

@HttpExchange("/shipments")
public interface ShipmentClient {

    @GetExchange("/{id}")
    ShipmentDto getShipmentById(@PathVariable("id") Long id);

    @GetExchange("/batch")
    ShipmentBatchResponse getShipmentsBatch(
            @RequestParam("ids") List<Long> ids
    );

    @PostExchange
    ShipmentDto createShipment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody CreateShipmentRequest request
    );
}