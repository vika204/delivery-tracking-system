package com.delivery.webui.controller;

import com.delivery.webui.client.ShipmentReadClient;
import com.delivery.webui.client.dto.ClientShipmentResponse;
import com.delivery.webui.controller.support.LoopbackOnly;
import com.delivery.webui.viewmodel.ParcelViewModel;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Controller
public class MyParcelsPageController {

    static final int PAGE_SIZE = 20;

    private final ShipmentReadClient shipmentClient;
    private final long senderId;

    public MyParcelsPageController(ShipmentReadClient shipmentClient,
                                  @Value("${shipment.creation.sender-id}") long senderId) {
        this.shipmentClient = shipmentClient;
        this.senderId = senderId;
    }

    @ModelAttribute
    void requireLocalAccess(HttpServletRequest request, HttpServletResponse response) {
        LoopbackOnly.require(request, "My Parcels");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    @GetMapping("/my-parcels")
    public String myParcels(@RequestParam(defaultValue = "0") int page, Model model) {
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must not be negative");
        }
        List<ClientShipmentResponse> shipments = shipmentClient.getMyShipments(senderId, page, PAGE_SIZE);
        if (shipments == null || shipments.size() > PAGE_SIZE || shipments.stream().anyMatch(
                shipment -> shipment == null || shipment.shipmentId() <= 0 || shipment.userId() != senderId)) {
            throw new RestClientException("Shipment service returned an invalid parcel list");
        }

        model.addAttribute("parcels", shipments.stream().map(ParcelViewModel::from).toList());
        model.addAttribute("page", page);
        model.addAttribute("hasNext", shipments.size() == PAGE_SIZE);
        return "pages/my-parcels";
    }

    @GetMapping("/my-parcels/{shipmentId}")
    public String parcelDetails(@PathVariable long shipmentId,
                                @RequestParam(required = false) String from, Model model) {
        if (shipmentId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "shipmentId must be positive");
        }
        ClientShipmentResponse shipment = shipmentClient.getShipment(shipmentId);
        if (shipment == null || shipment.shipmentId() != shipmentId) {
            throw new RestClientException("Shipment service returned invalid shipment details");
        }
        if (shipment.userId() != senderId) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shipment not found");
        }

        model.addAttribute("pageTitle", "Shipment #" + shipmentId + " — WEB EXPRESS");
        model.addAttribute("activePage", "notifications".equals(from) ? "notifications" : "my-parcels");
        model.addAttribute("parcel", ParcelViewModel.from(shipment));
        return "pages/shipment-details";
    }
}
