package com.delivery.dispatch.controller;

import com.delivery.dispatch.entity.Courier;
import com.delivery.dispatch.repository.CourierRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/couriers")
public class CourierController {

    private final CourierRepository courierRepository;

    public CourierController(CourierRepository courierRepository) {
        this.courierRepository = courierRepository;
    }

    @PostMapping
    public Courier createCourier(@RequestBody Courier courier) {
        return courierRepository.save(courier);
    }

    @GetMapping
    public List<Courier> getCouriers() {
        return courierRepository.findAll();
    }

    @GetMapping("/{id}")
    public Courier getCourier(@PathVariable Long id) {
        return courierRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Courier not found"));
    }
}
