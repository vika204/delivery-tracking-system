package com.delivery.tracking.controller;

import com.delivery.tracking.dto.RecordLocationRequest;
import com.delivery.tracking.dto.ShipmentLocationResponse;
import com.delivery.tracking.service.LocationRecordingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tracking/shipments")
public class LocationRecordingController {

    private final LocationRecordingService recordingService;

    public LocationRecordingController(LocationRecordingService recordingService) {
        this.recordingService = recordingService;
    }

    @PostMapping("/{shipmentId}/locations")
    public ResponseEntity<ShipmentLocationResponse> recordLocation(@PathVariable long shipmentId,
                                                           @RequestBody RecordLocationRequest request) {
        LocationRecordingService.Recorded recorded = recordingService.recordLocation(shipmentId, request);
        return ResponseEntity.status(recorded.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(recorded.location());
    }
}
