package com.delivery.webui.controller;

import com.delivery.webui.client.TrackingLocationClient;
import com.delivery.webui.client.dto.TrackingLocationResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

@RestController
@RequestMapping("/api/tracking/shipments")
@ConditionalOnProperty(
        prefix = "tracking.stream",
        name = "enabled",
        havingValue = "true"
)
public class TrackingLocationBackfillController {

    private static final int MAX_LIMIT = 100;
    private static final BigDecimal MIN_LATITUDE = new BigDecimal("-90");
    private static final BigDecimal MAX_LATITUDE = new BigDecimal("90");
    private static final BigDecimal MIN_LONGITUDE = new BigDecimal("-180");
    private static final BigDecimal MAX_LONGITUDE = new BigDecimal("180");

    private final TrackingLocationClient locationClient;

    public TrackingLocationBackfillController(
            TrackingLocationClient locationClient
    ) {
        this.locationClient = locationClient;
    }

    @GetMapping(
            path = "/{shipmentId}/locations",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public List<TrackingLocationResponse> getLocations(
            @PathVariable long shipmentId,
            @RequestParam(defaultValue = "0") long afterLocationId,
            @RequestParam(defaultValue = "100") int limit,
            HttpServletRequest request
    ) {
        validateQuery(shipmentId, afterLocationId, limit);

        if (!isLoopback(request.getRemoteAddr())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "GPS history is restricted to local development"
            );
        }

        try {
            List<TrackingLocationResponse> locations = locationClient.getShipmentLocations(shipmentId, afterLocationId, limit);
            validateResponse(locations, shipmentId, afterLocationId, limit);
            return locations;
        } catch (RestClientException ex) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "GPS history is temporarily unavailable",
                    ex
            );
        }
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

    private void validateResponse(List<TrackingLocationResponse> locations, long shipmentId, long afterLocationId, int limit) {
        if (locations == null || locations.size() > limit) {
            throw invalidResponse();
        }

        long previousLocationId = afterLocationId;

        for (TrackingLocationResponse location : locations) {
            if (location == null || location.locationId() == null || location.shipmentId() == null || location.shipmentId() != shipmentId || location.locationId() <= previousLocationId || !within(location.latitude(), MIN_LATITUDE, MAX_LATITUDE) || !within(location.longitude(), MIN_LONGITUDE, MAX_LONGITUDE)) {
                throw invalidResponse();
            }

            previousLocationId = location.locationId();
        }
    }

    private boolean within(BigDecimal value, BigDecimal minimum, BigDecimal maximum) {
        return value != null
                && value.compareTo(minimum) >= 0
                && value.compareTo(maximum) <= 0;
    }

    private ResponseStatusException badRequest(String detail) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
    }

    private ResponseStatusException invalidResponse() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "GPS history returned an invalid response");
    }

    private boolean isLoopback(String remoteAddress) {
        try {
            return remoteAddress != null && InetAddress.getByName(remoteAddress).isLoopbackAddress();
        } catch (UnknownHostException ex) {
            return false;
        }
    }
}
