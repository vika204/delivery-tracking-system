package com.delivery.webui.controller;

import com.delivery.webui.service.TrackingViewService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class TrackingPageController {

    private final TrackingViewService trackingViewService;

    public TrackingPageController(TrackingViewService trackingViewService) {
        this.trackingViewService = trackingViewService;
    }

    @GetMapping("/tracking")
    public String tracking(@RequestParam(required = false) String shipmentId, Model model) {
        if (shipmentId == null || shipmentId.isBlank()) {
            return "pages/tracking";
        }

        long id;
        try {
            id = Long.parseLong(shipmentId.trim());
        } catch (NumberFormatException _) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a numeric shipment ID, for example 15.");
        }
        if (id <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Shipment ID must be a positive number.");
        }

        model.addAttribute("tracking", trackingViewService.getLiveTracking(id));
        return "pages/tracking";
    }
}
