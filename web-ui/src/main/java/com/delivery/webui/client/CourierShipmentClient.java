package com.delivery.webui.client;

import com.delivery.webui.client.dto.CourierShipmentResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange("/shipments")
public interface CourierShipmentClient {

    @GetExchange("/{id}/courier-view")
    CourierShipmentResponse courierView(
            @RequestHeader("X-User-Id") long courierId,
            @PathVariable("id") long shipmentId
    );

    @PostExchange("/{id}/pickup")
    CourierShipmentResponse confirmPickup(
            @RequestHeader("X-User-Id") long courierId,
            @PathVariable("id") long shipmentId
    );

    @PostExchange("/{id}/deliver")
    CourierShipmentResponse completeDelivery(
            @RequestHeader("X-User-Id") long courierId,
            @PathVariable("id") long shipmentId
    );
}
