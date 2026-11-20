package com.delivery.webui.controller;

import com.delivery.webui.config.ShipmentCreationProperties;
import com.delivery.webui.client.ShipmentCreationClient;
import com.delivery.webui.client.dto.CreateShipmentRequest;
import com.delivery.webui.client.dto.CreatedShipmentResponse;
import com.delivery.webui.client.dto.ShipmentQuoteRequest;
import com.delivery.webui.client.dto.ShipmentQuoteResponse;
import com.delivery.webui.controller.support.LocalDevOnly;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * BFF форми «Create Shipment»: розрахунок вартості й підтвердження. Поля перевіряє shipment-service,
 * його помилки передаються сторінці як є. Лише для локальної розробки:
 * відправник задається властивістю shipment.creation.sender-id, доки немає автентифікації.
 */
@RestController
@RequestMapping("/api/shipments")
@ConditionalOnProperty(prefix = "shipment.creation", name = "enabled", havingValue = "true")
@LocalDevOnly("Shipment creation API")
public class ShipmentCreationApiController {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    private static final String REPLAYED = "Idempotent-Replayed";

    private final ShipmentCreationClient client;
    private final long senderId;

    public ShipmentCreationApiController(ShipmentCreationClient client,
                                         ShipmentCreationProperties properties) {
        this.client = client;
        this.senderId = properties.senderId();
    }

    @PostMapping("/quotes")
    public ShipmentQuoteResponse quote(@RequestBody ShipmentQuoteRequest request) {
        return client.quote(senderId, request);
    }

    @PostMapping
    public ResponseEntity<CreatedShipmentResponse> create(
            @RequestHeader(IDEMPOTENCY_KEY) String idempotencyKey,
            @RequestBody CreateShipmentRequest request) {
        ResponseEntity<CreatedShipmentResponse> creation = client.create(senderId, idempotencyKey, request);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if ("true".equals(creation.getHeaders().getFirst(REPLAYED))) {
            response.header(REPLAYED, "true");
        }
        return response.body(creation.getBody());
    }
}
