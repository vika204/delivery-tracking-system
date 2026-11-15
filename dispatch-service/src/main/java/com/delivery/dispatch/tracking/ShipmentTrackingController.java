package com.delivery.dispatch.tracking;

import com.delivery.dispatch.tracking.dto.LatestLocationResponse;
import com.delivery.dispatch.tracking.dto.RoutePointRequest;
import com.delivery.dispatch.tracking.dto.RouteUploadRequest;
import com.delivery.dispatch.tracking.dto.RouteUploadResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * HTTP-фасад кур'єрської сторони над gRPC-викликами tracking-service.
 * GET  /dispatch/shipments/{id}/location -> Unary GetLatestShipmentLocation
 * POST /dispatch/shipments/{id}/route    -> Client Streaming RecordShipmentRoute
 */
@RestController
@RequestMapping("/dispatch/shipments")
public class ShipmentTrackingController {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 64;

    private final TrackingGrpcClient trackingClient;
    private final int maxRoutePoints;

    public ShipmentTrackingController(
            TrackingGrpcClient trackingClient,
            @Value("${dispatch.route-upload.max-points:1000}") int maxRoutePoints
    ) {
        this.trackingClient = trackingClient;
        this.maxRoutePoints = maxRoutePoints;
    }

    @GetMapping("/{shipmentId}/location")
    public LatestLocationResponse getLatestLocation(@PathVariable long shipmentId) {
        requirePositive(shipmentId);
        return trackingClient.getLatestLocation(shipmentId);
    }

    @PostMapping("/{shipmentId}/route")
    public RouteUploadResponse uploadRoute(
            @PathVariable long shipmentId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody RouteUploadRequest request
    ) {
        requirePositive(shipmentId);
        List<RoutePointRequest> points = validatePoints(request);

        String uploadId = (idempotencyKey == null || idempotencyKey.isBlank())
                ? UUID.randomUUID().toString()
                : idempotencyKey.trim();

        if (uploadId.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw badRequest("Idempotency-Key must be at most " + MAX_IDEMPOTENCY_KEY_LENGTH + " characters");
        }

        return trackingClient.uploadRoute(shipmentId, uploadId, points);
    }

    private List<RoutePointRequest> validatePoints(RouteUploadRequest request) {
        if (request == null || request.points() == null || request.points().isEmpty()) {
            throw badRequest("points must not be empty");
        }
        if (request.points().size() > maxRoutePoints) {
            throw badRequest("points must contain at most " + maxRoutePoints + " items");
        }
        for (RoutePointRequest point : request.points()) {
            if (point == null || point.latitude() == null || point.longitude() == null || point.recordedAt() == null) {
                throw badRequest("each point must have latitude, longitude and recordedAt");
            }
            if (point.latitude() < -90 || point.latitude() > 90) {
                throw badRequest("latitude must be between -90 and 90");
            }
            if (point.longitude() < -180 || point.longitude() > 180) {
                throw badRequest("longitude must be between -180 and 180");
            }
        }
        return request.points();
    }

    private static void requirePositive(long shipmentId) {
        if (shipmentId <= 0) {
            throw badRequest("shipmentId must be positive");
        }
    }

    private static ResponseStatusException badRequest(String detail) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
    }
}
