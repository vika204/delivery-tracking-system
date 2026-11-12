package com.delivery.webui.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class CreateShipmentPageController {

    @GetMapping("/ship")
    public String createShipmentPage(Model model) {
        model.addAttribute("pageTitle", "Create Shipment — WEB EXPRESS");
        model.addAttribute("activePage", "ship");
        model.addAttribute("authenticated", true);
        return "pages/create-shipment";
    }
}
