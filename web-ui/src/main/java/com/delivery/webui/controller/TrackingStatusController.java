package com.delivery.webui.controller;

import com.delivery.webui.client.TrackingShipmentClient;
import com.delivery.webui.client.dto.TrackingShipmentResponse;
import com.delivery.webui.controller.dto.TrackingShipmentStatusResponse;
import com.delivery.webui.controller.support.LoopbackOnly;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@RestController
@RequestMapping("/api/tracking/shipments")
public class TrackingStatusController {

    private final TrackingShipmentClient shipmentClient;

    public TrackingStatusController(TrackingShipmentClient shipmentClient) {
        this.shipmentClient = shipmentClient;
    }

    @GetMapping(
            path = "/{shipmentId}/status",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public TrackingShipmentStatusResponse getStatus(
            @PathVariable long shipmentId,
            HttpServletRequest request
    ) {
        if (shipmentId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "shipmentId must be positive");
        }

        LoopbackOnly.require(request, "Shipment status");

        TrackingShipmentResponse shipment = shipmentClient.getShipmentById(shipmentId);

        if (shipment == null || shipment.shipmentId() == null
                || shipment.shipmentId() != shipmentId
                || shipment.status() == null || shipment.status().isBlank()
                || "UNAVAILABLE".equalsIgnoreCase(shipment.status().trim())) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Shipment status is temporarily unavailable"
            );
        }

        return new TrackingShipmentStatusResponse(
                shipmentId,
                shipment.status().trim().toUpperCase(Locale.ROOT)
        );
    }
}
