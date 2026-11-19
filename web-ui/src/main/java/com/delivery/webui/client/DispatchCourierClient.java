package com.delivery.webui.client;

import com.delivery.webui.client.dto.DispatchCourierResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange("/dispatch/shipments")
public interface DispatchCourierClient {

    @GetExchange("/{shipmentId}/courier")
    DispatchCourierResponse getShipmentCourier(@PathVariable("shipmentId") long shipmentId);
}
