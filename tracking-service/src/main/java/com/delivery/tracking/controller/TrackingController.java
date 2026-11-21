package com.delivery.tracking.controller;

import com.delivery.tracking.client.dto.CreateShipmentRequest;
import com.delivery.tracking.client.dto.ShipmentBatchResponse;
import com.delivery.tracking.client.dto.ShipmentDto;
import com.delivery.tracking.entity.Location;
import com.delivery.tracking.service.LocationService;
import com.delivery.tracking.service.TrackingService;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/tracking")
public class TrackingController {

    private final LocationService locationService;
    private final TrackingService trackingService;
    private static final Logger log = LoggerFactory.getLogger(TrackingController.class);

    public TrackingController(LocationService locationService, TrackingService trackingService) {
        this.locationService = locationService;
        this.trackingService = trackingService;
    }

    @GetMapping
    public List<Location> getLocations() {
        return locationService.getLocations();
    }

    @GetMapping("/{id}")
    public Location getLocation(@PathVariable Long id) {
        return locationService.getLocation(id);
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
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody CreateShipmentRequest request) {
        String key = (idempotencyKey == null || idempotencyKey.isBlank())
                ? UUID.randomUUID().toString()
                : idempotencyKey;
        log.info("Tracking shipment registration for user {} with idempotencyKey {}", userId, key);

        return trackingService.createShipment(key, userId, request);
    }

}
