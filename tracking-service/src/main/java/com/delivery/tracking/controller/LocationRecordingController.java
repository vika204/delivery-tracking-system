package com.delivery.tracking.controller;

import com.delivery.tracking.dto.RecordLocationRequest;
import com.delivery.tracking.dto.ShipmentLocationResponse;
import com.delivery.tracking.service.LocationRecordingService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Запис GPS-точки кур'єра. Внутрішній API: що посилка призначена цьому кур'єру й уже в дорозі,
 * перевіряє викликач (web-ui через shipment-service); тут — коректність самої точки.
 */
@RestController
@RequestMapping("/tracking/shipments")
public class LocationRecordingController {

    private final LocationRecordingService recordingService;

    public LocationRecordingController(LocationRecordingService recordingService) {
        this.recordingService = recordingService;
    }

    @PostMapping("/{shipmentId}/locations")
    @ResponseStatus(HttpStatus.CREATED)
    public ShipmentLocationResponse record(@PathVariable long shipmentId, @RequestBody RecordLocationRequest request) {
        return recordingService.record(shipmentId, request);
    }
}
