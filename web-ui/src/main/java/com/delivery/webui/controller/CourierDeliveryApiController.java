package com.delivery.webui.controller;

import com.delivery.webui.config.CourierDashboardProperties;
import com.delivery.webui.client.dto.CourierShipmentResponse;
import com.delivery.webui.client.dto.RecordLocationRequest;
import com.delivery.webui.client.dto.TrackingLocationResponse;
import com.delivery.webui.controller.support.LocalDevOnly;
import com.delivery.webui.security.AuthenticatedUser;
import com.delivery.webui.service.CourierDeliveryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/courier/shipments")
@ConditionalOnProperty(prefix = "dispatch.courier-dashboard", name = "enabled", havingValue = "true")
@LocalDevOnly("Courier delivery API")
public class CourierDeliveryApiController {

    private final CourierDeliveryService deliveryService;
    private final long courierId;

    public CourierDeliveryApiController(CourierDeliveryService deliveryService,
                                        CourierDashboardProperties properties) {
        this.deliveryService = deliveryService;
        this.courierId = properties.courierId();
    }

    @GetMapping("/{shipmentId}")
    public CourierShipmentResponse shipment(@PathVariable long shipmentId) {
        return deliveryService.view(AuthenticatedUser.currentUserId(courierId), shipmentId);
    }

    @PostMapping("/{shipmentId}/pickup")
    public CourierShipmentResponse confirmPickup(@PathVariable long shipmentId) {
        return deliveryService.confirmPickup(AuthenticatedUser.currentUserId(courierId), shipmentId);
    }

    @PostMapping("/{shipmentId}/deliver")
    public CourierShipmentResponse completeDelivery(@PathVariable long shipmentId) {
        return deliveryService.completeDelivery(AuthenticatedUser.currentUserId(courierId), shipmentId);
    }

    @PostMapping("/{shipmentId}/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@PathVariable long shipmentId) {
        deliveryService.withdraw(AuthenticatedUser.currentUserId(courierId), shipmentId);
    }

    @PostMapping("/{shipmentId}/locations")
    public ResponseEntity<TrackingLocationResponse> recordLocation(@PathVariable long shipmentId,
                                                                   @RequestBody RecordLocationRequest point) {
        ResponseEntity<TrackingLocationResponse> recorded = deliveryService.recordLocation(AuthenticatedUser.currentUserId(courierId), shipmentId, point);
        return ResponseEntity.status(recorded.getStatusCode().value() == 200 ? HttpStatus.OK : HttpStatus.CREATED)
                .body(recorded.getBody());
    }

}
