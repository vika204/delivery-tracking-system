package com.delivery.webui.controller;

import com.delivery.webui.client.dto.CourierShipmentResponse;
import com.delivery.webui.client.dto.RecordLocationRequest;
import com.delivery.webui.client.dto.RecordedLocationResult;
import com.delivery.webui.client.dto.TrackingLocationResponse;
import com.delivery.webui.controller.support.LoopbackOnly;
import com.delivery.webui.service.CourierDeliveryService;
import com.delivery.webui.service.CourierDeliveryService.ShipmentNotInTransitException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * BFF сторінки доставки кур'єра. Лише для локальної розробки: той самий кур'єр, що й у
 * Courier Dashboard (dispatch.courier-dashboard.courier-id), доки немає автентифікації.
 */
@RestController
@RequestMapping("/api/courier/shipments")
@ConditionalOnProperty(prefix = "dispatch.courier-dashboard", name = "enabled", havingValue = "true")
public class CourierDeliveryApiController {

    private final CourierDeliveryService deliveryService;
    private final long courierId;

    public CourierDeliveryApiController(CourierDeliveryService deliveryService,
                                        @Value("${dispatch.courier-dashboard.courier-id}") long courierId) {
        this.deliveryService = deliveryService;
        this.courierId = courierId;
    }

    @ModelAttribute
    void requireLoopback(HttpServletRequest request) {
        LoopbackOnly.require(request, "Courier delivery API");
    }

    @GetMapping("/{shipmentId}")
    public CourierShipmentResponse shipment(@PathVariable long shipmentId) {
        return deliveryService.view(courierId, shipmentId);
    }

    @PostMapping("/{shipmentId}/pickup")
    public CourierShipmentResponse confirmPickup(@PathVariable long shipmentId) {
        return deliveryService.confirmPickup(courierId, shipmentId);
    }

    @PostMapping("/{shipmentId}/deliver")
    public CourierShipmentResponse completeDelivery(@PathVariable long shipmentId) {
        return deliveryService.completeDelivery(courierId, shipmentId);
    }

    /** 201 — нова точка, 200 — повтор тієї самої точки (pointId), дубля немає. */
    @PostMapping("/{shipmentId}/locations")
    public ResponseEntity<TrackingLocationResponse> recordLocation(@PathVariable long shipmentId,
                                                                   @RequestBody RecordLocationRequest point) {
        RecordedLocationResult recorded = deliveryService.recordLocation(courierId, shipmentId, point);
        return ResponseEntity.status(recorded.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(recorded.location());
    }

    @ExceptionHandler(ShipmentNotInTransitException.class)
    ProblemDetail notInTransit(ShipmentNotInTransitException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(URI.create("urn:delivery:problem:shipment-not-in-transit"));
        return problem;
    }
}
