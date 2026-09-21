package com.delivery.tracking.controller;

import com.delivery.tracking.entity.Location;
import com.delivery.tracking.repository.TrackingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/tracking")
public class TrackingController {

    private final TrackingRepository trackingRepository;

    public TrackingController(TrackingRepository trackingRepository) {
        this.trackingRepository = trackingRepository;
    }

    @PostMapping
    public Location createLocation(@RequestBody Location location) {
        return trackingRepository.save(location);
    }

    @GetMapping
    public List<Location> getLocations() {
        return trackingRepository.findAll();
    }

    @GetMapping("/{id}")
    public Location getLocation(@PathVariable Long id) {
        return trackingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

}
