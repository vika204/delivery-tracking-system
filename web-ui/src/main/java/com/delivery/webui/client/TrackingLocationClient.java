package com.delivery.webui.client;

import com.delivery.webui.client.dto.RecordLocationRequest;
import com.delivery.webui.client.dto.TrackingLocationResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

@HttpExchange("/tracking/shipments/{shipmentId}/locations")
public interface TrackingLocationClient {

    @GetExchange
    List<TrackingLocationResponse> getShipmentLocations(
            @PathVariable("shipmentId") long shipmentId,
            @RequestParam("afterLocationId") long afterLocationId,
            @RequestParam("limit") int limit
    );

    @PostExchange(contentType = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<TrackingLocationResponse> recordLocation(
            @PathVariable("shipmentId") long shipmentId,
            @RequestBody RecordLocationRequest request
    );
}
