package com.delivery.tracking.controller;

import com.delivery.tracking.client.dto.CreateShipmentRequest;
import com.delivery.tracking.client.dto.ShipmentBatchResponse;
import com.delivery.tracking.client.dto.ShipmentDto;
import com.delivery.tracking.entity.Location;
import com.delivery.tracking.repository.TrackingRepository;
import com.delivery.tracking.service.TrackingService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/tracking")
public class TrackingController {

    private final TrackingRepository trackingRepository;
    private final TrackingService trackingService;

    private static final Logger log = LoggerFactory.getLogger(TrackingController.class);

    public TrackingController(
            TrackingRepository trackingRepository,
            TrackingService trackingService) {
        this.trackingRepository = trackingRepository;
        this.trackingService = trackingService;
    }

    @PostMapping
    public Location createLocation(@RequestBody Location location) {
        return trackingRepository.save(location);
    }

    @GetMapping
    public List<Location> getLocations() {
        return trackingRepository.findAll();
    }

    @GetMapping("/{id}")
    public Location getLocation(@PathVariable Long id) {
        return trackingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/shipments/{id}")
    public ShipmentDto getShipment(@PathVariable Long id) {
        log.info("Tracking request for shipment {}", id);

        return trackingService.getShipmentById(id);
    }

    @GetMapping("/shipments/batch")
    public ShipmentBatchResponse getShipmentsBatch(@RequestParam List<Long> ids) {
        log.info("Tracking batch request for {} shipments", ids.size());

        return trackingService.getShipmentsBatch(ids);
    }

    @PostMapping("/shipments")
    public ShipmentDto createShipment(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody CreateShipmentRequest request) {
        String key = (idempotencyKey == null || idempotencyKey.isBlank())
                ? UUID.randomUUID().toString()
                : idempotencyKey;
        log.info("Tracking shipment registration for user {} with idempotencyKey {}", request.userId(), key);

        return trackingService.createShipment(key, request);
    }

}
