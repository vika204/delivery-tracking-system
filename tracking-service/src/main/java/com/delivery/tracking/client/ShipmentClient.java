package com.delivery.tracking.client;

import com.delivery.tracking.client.dto.ShipmentDto;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange("/shipments")
public interface ShipmentClient {

    @GetExchange("/{id}")
    ShipmentDto getShipmentById(@PathVariable("id") Long id);
}