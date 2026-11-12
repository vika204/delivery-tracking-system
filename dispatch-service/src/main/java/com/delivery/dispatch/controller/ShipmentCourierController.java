package com.delivery.dispatch.controller;

import com.delivery.dispatch.dto.ShipmentCourierResponse;
import com.delivery.dispatch.service.ShipmentCourierService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/dispatch/shipments")
@ConditionalOnProperty(
        prefix = "dispatch.courier-lookup",
        name = "enabled",
        havingValue = "true"
)
public class ShipmentCourierController {

    private final ShipmentCourierService shipmentCourierService;

    public ShipmentCourierController(
            ShipmentCourierService shipmentCourierService
    ) {
        this.shipmentCourierService = shipmentCourierService;
    }

    @GetMapping(
            path = "/{shipmentId}/courier",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ShipmentCourierResponse getCurrentCourier(
            @PathVariable long shipmentId
    ) {
        if (shipmentId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "shipmentId must be positive"
            );
        }

        return shipmentCourierService.getCurrentCourier(shipmentId);
    }
}
