package com.delivery.shipment.controller;

import com.delivery.shipment.dto.CreateShipmentRequest;
import com.delivery.shipment.dto.ShipmentBatchResponse;
import com.delivery.shipment.dto.ShipmentResponse;
import com.delivery.shipment.service.ShipmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/shipments")
public class ShipmentController {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String IDEMPOTENT_REPLAYED_HEADER = "Idempotent-Replayed";

    private final ShipmentService shipmentService;

    public ShipmentController(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    @PostMapping
    public ResponseEntity<ShipmentResponse> createShipment(
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER) @Size(min = 1, max = 255) String idempotencyKey,
            @Valid @RequestBody CreateShipmentRequest request) {
        ShipmentService.CreationResult result = shipmentService.create(idempotencyKey, request);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (result.replayed()) {
            response.header(IDEMPOTENT_REPLAYED_HEADER, "true");
        }
        return response.body(ShipmentResponse.from(result.shipment()));
    }

    @GetMapping
    public List<ShipmentResponse> getShipments() {
        return shipmentService.getAll().stream().map(ShipmentResponse::from).toList();
    }

    @GetMapping("/batch")
    public ShipmentBatchResponse getShipmentsBatch(
            @RequestParam @Size(min = 1, max = 100) List<@Positive Long> ids) {
        return shipmentService.getBatch(ids);
    }

    @GetMapping("/{id}")
    public ShipmentResponse getShipment(@PathVariable Long id) {
        return ShipmentResponse.from(shipmentService.getById(id));
    }
}
