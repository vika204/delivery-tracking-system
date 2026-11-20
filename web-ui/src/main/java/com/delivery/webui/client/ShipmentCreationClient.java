package com.delivery.webui.client;

import com.delivery.webui.client.dto.CreateShipmentRequest;
import com.delivery.webui.client.dto.CreatedShipmentResponse;
import com.delivery.webui.client.dto.ShipmentQuoteRequest;
import com.delivery.webui.client.dto.ShipmentQuoteResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange("/shipments")
public interface ShipmentCreationClient {

    @PostExchange(value = "/quotes", contentType = MediaType.APPLICATION_JSON_VALUE)
    ShipmentQuoteResponse quote(
            @RequestHeader("X-User-Id") long senderId,
            @RequestBody ShipmentQuoteRequest request
    );

    @PostExchange(contentType = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<CreatedShipmentResponse> create(
            @RequestHeader("X-User-Id") long senderId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody CreateShipmentRequest request
    );
}
