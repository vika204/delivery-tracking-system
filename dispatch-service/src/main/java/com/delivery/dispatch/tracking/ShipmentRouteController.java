package com.delivery.dispatch.tracking;

import com.delivery.dispatch.tracking.dto.LiveLocationPointRequest;
import com.delivery.dispatch.tracking.dto.LiveLocationStreamRequest;
import com.delivery.dispatch.tracking.dto.LiveLocationStreamResponse;
import com.delivery.dispatch.tracking.dto.RoutePointRequest;
import com.delivery.dispatch.tracking.dto.RouteUploadRequest;
import com.delivery.dispatch.tracking.dto.RouteUploadResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
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
 * Імітація застосунку кур'єра:
 * <ul>
 *   <li>{@code POST /dispatch/shipments/{id}/route} -> Client Streaming {@code RecordShipmentRoute}
 *       (вивантаження накопичених координат після офлайну);</li>
 *   <li>{@code POST /dispatch/shipments/{id}/live-locations} -> Bidirectional Streaming {@code StreamCourierLocations}
 *       (передача координат у реальному часі з миттєвою відповіддю сервера по кожній точці).</li>
 * </ul>
 */
@RestController
@RequestMapping("/dispatch/shipments")
public class ShipmentRouteController {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 64;

    private final ShipmentRouteGrpcClient routeClient;
    private final CourierLiveTrackingGrpcClient liveTrackingClient;
    private final int maxRoutePoints;

    public ShipmentRouteController(
            ShipmentRouteGrpcClient routeClient,
            @Value("${dispatch.route-upload.max-points:1000}") int maxRoutePoints
    ) {
        this(routeClient, null, maxRoutePoints);
    }

    @Autowired
    public ShipmentRouteController(
            ShipmentRouteGrpcClient routeClient,
            CourierLiveTrackingGrpcClient liveTrackingClient,
            @Value("${dispatch.route-upload.max-points:1000}") int maxRoutePoints
    ) {
        this.routeClient = routeClient;
        this.liveTrackingClient = liveTrackingClient;
        this.maxRoutePoints = maxRoutePoints;
    }

    @PostMapping("/{shipmentId}/route")
    public RouteUploadResponse uploadRoute(
            @PathVariable long shipmentId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody RouteUploadRequest request
    ) {
        if (shipmentId <= 0) {
            throw badRequest("shipmentId must be positive");
        }

        List<RoutePointRequest> points = request == null ? null : request.points();
        if (points == null || points.isEmpty()) {
            throw badRequest("points must not be empty");
        }
        if (points.size() > maxRoutePoints) {
            throw badRequest("points must contain at most " + maxRoutePoints + " items");
        }

        String uploadId = (idempotencyKey == null || idempotencyKey.isBlank())
                ? UUID.randomUUID().toString()
                : idempotencyKey.trim();

        if (uploadId.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw badRequest("Idempotency-Key must be at most " + MAX_IDEMPOTENCY_KEY_LENGTH + " characters");
        }

        return routeClient.uploadRoute(shipmentId, uploadId, points);
    }

    @PostMapping("/{shipmentId}/live-locations")
    public LiveLocationStreamResponse streamLiveLocations(
            @PathVariable long shipmentId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody LiveLocationStreamRequest request
    ) {
        if (shipmentId <= 0) {
            throw badRequest("shipmentId must be positive");
        }

        List<LiveLocationPointRequest> points = request == null ? null : request.points();
        if (points == null || points.isEmpty()) {
            throw badRequest("points must not be empty");
        }
        if (points.size() > maxRoutePoints) {
            throw badRequest("points must contain at most " + maxRoutePoints + " items");
        }

        String sessionId = (idempotencyKey == null || idempotencyKey.isBlank())
                ? UUID.randomUUID().toString()
                : idempotencyKey.trim();

        if (sessionId.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw badRequest("Idempotency-Key must be at most " + MAX_IDEMPOTENCY_KEY_LENGTH + " characters");
        }

        return liveTrackingClient.streamLocations(shipmentId, sessionId, points);
    }

    private static ResponseStatusException badRequest(String detail) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
    }
}
