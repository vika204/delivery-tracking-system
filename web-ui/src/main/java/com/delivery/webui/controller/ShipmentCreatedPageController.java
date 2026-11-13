package com.delivery.webui.controller;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class ShipmentCreatedPageController {

    @GetMapping("/ship/success/{shipmentId}")
    public String shipmentCreatedPage(@PathVariable long shipmentId, Model model) {
        if (shipmentId <= 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        model.addAttribute("pageTitle", "Shipment Created — WEB EXPRESS");
        model.addAttribute("activePage", "ship");
        model.addAttribute("authenticated", true);
        model.addAttribute("shipmentId", shipmentId);
        return "pages/shipment-created";
    }
}
