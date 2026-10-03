package com.delivery.tracking.service;

import com.delivery.tracking.client.ShipmentClient;
import com.delivery.tracking.client.dto.ShipmentDto;
import org.springframework.stereotype.Service;

@Service
public class TrackingService {

    private final ShipmentClient shipmentClient;

    public TrackingService(ShipmentClient shipmentClient) {
        this.shipmentClient = shipmentClient;
    }

    public ShipmentDto getShipmentById(Long shipmentId) {
        return shipmentClient.getShipmentById(shipmentId);
    }
}