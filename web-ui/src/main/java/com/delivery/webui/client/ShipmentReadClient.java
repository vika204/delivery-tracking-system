package com.delivery.webui.client;

import com.delivery.webui.client.dto.ClientShipmentResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.List;

@HttpExchange("/shipments")
public interface ShipmentReadClient {

    @GetExchange("/mine")
    List<ClientShipmentResponse> getMyShipments(
            @RequestHeader("X-User-Id") long senderId,
            @RequestParam("page") int page,
            @RequestParam("size") int size
    );

    @GetExchange("/{id}")
    ClientShipmentResponse getShipment(@PathVariable("id") long shipmentId);
}
