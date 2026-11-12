package com.delivery.tracking.controller;

import com.delivery.tracking.dto.ShipmentLocationResponse;
import com.delivery.tracking.service.LocationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/tracking/shipments")
@ConditionalOnProperty(
        prefix = "tracking.location-history",
        name = "enabled",
        havingValue = "true"
)
public class ShipmentLocationController {

    private static final int MAX_LIMIT = 100;
    private final LocationService locationService;

    public ShipmentLocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @GetMapping("/{shipmentId}/locations")
    public List<ShipmentLocationResponse> getShipmentLocations(
            @PathVariable("shipmentId") long shipmentId,
            @RequestParam(name = "afterLocationId", defaultValue = "0") long afterLocationId,
            @RequestParam(name = "limit", defaultValue = "100") int limit
    ) {
        validateQuery(shipmentId, afterLocationId, limit);

        return locationService.getShipmentLocations(
                shipmentId,
                afterLocationId,
                limit
        );
    }

    private void validateQuery(long shipmentId, long afterLocationId, int limit) {
        if (shipmentId <= 0) {
            throw badRequest("shipmentId must be positive");
        }

        if (afterLocationId < 0) {
            throw badRequest("afterLocationId must be zero or positive");
        }

        if (limit < 1 || limit > MAX_LIMIT) {
            throw badRequest("limit must be between 1 and " + MAX_LIMIT);
        }
    }

    private ResponseStatusException badRequest(String detail) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                detail
        );
    }
}