package com.delivery.notification.controller;

import com.delivery.notification.dto.DeliveryInfoResponse;
import com.delivery.notification.service.DeliveryTrackingService;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notifications/tracking")
public class ShipmentTrackingController {

    private final DeliveryTrackingService deliveryTrackingService;

    public ShipmentTrackingController(DeliveryTrackingService deliveryTrackingService) {
        this.deliveryTrackingService = deliveryTrackingService;
    }

    @GetMapping("/shipments/{shipmentId}/delivery-info")
    public DeliveryInfoResponse getDeliveryInfo(@PathVariable @Positive long shipmentId) {
        return deliveryTrackingService.getDeliveryInfo(shipmentId);
    }

    @PostMapping("/subscriptions/{shipmentId}")
    public ResponseEntity<Void> subscribe(@PathVariable @Positive long shipmentId) {
        return deliveryTrackingService.subscribe(shipmentId)
                ? ResponseEntity.accepted().build()
                : ResponseEntity.ok().build();
    }

    @DeleteMapping("/subscriptions/{shipmentId}")
    public ResponseEntity<Void> unsubscribe(@PathVariable long shipmentId) {
        return deliveryTrackingService.unsubscribe(shipmentId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
