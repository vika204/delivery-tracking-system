package com.delivery.webui.controller;

import com.delivery.webui.config.ShipmentCreationProperties;
import com.delivery.webui.client.ClientShipmentClient;
import com.delivery.webui.controller.support.LocalDevOnly;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shipments")
@LocalDevOnly("Client shipment API")
public class ClientShipmentApiController {

    private final ClientShipmentClient client;
    private final long senderId;

    public ClientShipmentApiController(ClientShipmentClient client,
                                       ShipmentCreationProperties properties) {
        this.client = client;
        this.senderId = properties.senderId();
    }

    @ModelAttribute
    void noStore(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    @PostMapping("/{shipmentId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelShipment(@PathVariable long shipmentId) {
        client.cancelShipment(senderId, shipmentId);
    }
}
