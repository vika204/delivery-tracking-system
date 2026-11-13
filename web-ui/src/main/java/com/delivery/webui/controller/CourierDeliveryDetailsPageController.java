package com.delivery.webui.controller;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class CourierDeliveryDetailsPageController {

    @GetMapping("/courier/deliveries/{shipmentId}")
    public String courierDeliveryDetails(@PathVariable long shipmentId, Model model) {
        if (shipmentId <= 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        model.addAttribute("pageTitle", "Delivery Details — WEB EXPRESS");
        model.addAttribute("shipmentId", shipmentId);
        model.addAttribute("activePage", "courier-dashboard");
        model.addAttribute("headerState", "COURIER");
        return "pages/courier-delivery-details";
    }
}
