package com.delivery.dispatch.controller;

import com.delivery.dispatch.dto.LiveLocationStreamResponse;
import com.delivery.dispatch.dto.LocationPointsRequest;
import com.delivery.dispatch.dto.RouteUploadResponse;
import com.delivery.dispatch.service.CourierTrackingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dispatch/shipments/{shipmentId}")
public class CourierTrackingController {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final CourierTrackingService courierTrackingService;

    public CourierTrackingController(CourierTrackingService courierTrackingService) {
        this.courierTrackingService = courierTrackingService;
    }

    @PostMapping("/route")
    public RouteUploadResponse uploadRoute(
            @PathVariable @Positive long shipmentId,
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) @Size(max = 64) String idempotencyKey,
            @Valid @RequestBody LocationPointsRequest request) {
        return courierTrackingService.uploadRoute(shipmentId, idempotencyKey, request.points());
    }

    @PostMapping("/live-locations")
    public LiveLocationStreamResponse streamLiveLocations(
            @PathVariable @Positive long shipmentId,
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) @Size(max = 64) String idempotencyKey,
            @Valid @RequestBody LocationPointsRequest request) {
        return courierTrackingService.streamLiveLocations(shipmentId, idempotencyKey, request.points());
    }
}
