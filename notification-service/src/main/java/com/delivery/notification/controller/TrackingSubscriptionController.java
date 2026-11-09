package com.delivery.notification.controller;

import com.delivery.notification.grpc.ShipmentLocationGrpcClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/notifications/tracking/subscriptions")
public class TrackingSubscriptionController {

    private final ShipmentLocationGrpcClient grpcClient;

    public TrackingSubscriptionController(ShipmentLocationGrpcClient grpcClient) {
        this.grpcClient = grpcClient;
    }

    @PostMapping("/{shipmentId}")
    public ResponseEntity<Void> subscribe(@PathVariable long shipmentId) {
        if (shipmentId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "shipmentId must be positive");
        }
        boolean started = grpcClient.subscribe(shipmentId);
        return started ? ResponseEntity.accepted().build() : ResponseEntity.ok().build();
    }

    @DeleteMapping("/{shipmentId}")
    public ResponseEntity<Void> unsubscribe(@PathVariable long shipmentId) {
        boolean stopped = grpcClient.unsubscribe(shipmentId);
        return stopped ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}