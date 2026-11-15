package com.delivery.notification.controller;

import com.delivery.notification.grpc.ShipmentDeliveryInfoGrpcClient;
import com.delivery.notification.grpc.dto.DeliveryInfoResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * HTTP-ендпоїнт для перевірки Unary-виклику GetShipmentDeliveryInfo.
 */
@RestController
@RequestMapping("/notifications/tracking/shipments")
public class ShipmentDeliveryInfoController {

    private final ShipmentDeliveryInfoGrpcClient grpcClient;

    public ShipmentDeliveryInfoController(ShipmentDeliveryInfoGrpcClient grpcClient) {
        this.grpcClient = grpcClient;
    }

    @GetMapping("/{shipmentId}/delivery-info")
    public DeliveryInfoResponse getDeliveryInfo(@PathVariable long shipmentId) {
        if (shipmentId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "shipmentId must be positive");
        }
        return grpcClient.getDeliveryInfo(shipmentId);
    }
}
