package com.delivery.shipment.controller;

import com.delivery.shipment.entity.Shipment;
import com.delivery.shipment.repository.ShipmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/shipments")
public class ShipmentController {

    private final ShipmentRepository shipmentRepository;

    public ShipmentController(ShipmentRepository shipmentRepository) {
        this.shipmentRepository = shipmentRepository;
    }

    @PostMapping
    public Shipment createShipment(@RequestBody Shipment shipment) {
        return shipmentRepository.save(shipment);
    }

    @GetMapping
    public List<Shipment> getShipments() {
        return shipmentRepository.findAll();
    }

    @GetMapping("/{id}")
    public Shipment getShipment(@PathVariable Long id) {
        return shipmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shipment not found"));
    }
}
