package com.delivery.webui.client;

import com.delivery.webui.client.dto.TrackingShipmentBatchResponse;
import com.delivery.webui.client.dto.TrackingShipmentResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange("/tracking/shipments")
public interface TrackingShipmentClient {

    @GetExchange("/{id}")
    TrackingShipmentResponse getShipmentById(@PathVariable("id") long shipmentId);

    @GetExchange("/batch")
    TrackingShipmentBatchResponse getShipments(@RequestParam("ids") long shipmentId);
}
