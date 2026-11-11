package com.delivery.webui.controller;

import com.delivery.webui.service.TrackingViewService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

@Controller
public class TrackingPageController {

    private final TrackingViewService trackingViewService;

    public TrackingPageController(TrackingViewService trackingViewService) {
        this.trackingViewService = trackingViewService;
    }

    @GetMapping("/tracking")
    public String tracking(
            @RequestParam(required = false) String shipmentId,
            Model model
    ) {
        model.addAttribute(
                "pageTitle",
                "Track your delivery — WEB EXPRESS"
        );
        model.addAttribute("activePage", "tracking");

        String trackingInput = shipmentId == null ? "" : shipmentId.trim();

        model.addAttribute("trackingInput", trackingInput);
        model.addAttribute("tracking", trackingViewService.getEmptyTracking());

        if (trackingInput.isBlank()) {
            return "pages/tracking";
        }

        try {
            long id = Long.parseLong(trackingInput);

            if (id <= 0) {
                model.addAttribute(
                        "errorMessage",
                        "Shipment ID must be a positive number."
                );
                return "pages/tracking";
            }

            model.addAttribute(
                    "tracking",
                    trackingViewService.getLiveTracking(id)
            );

        } catch (NumberFormatException ex) {
            model.addAttribute(
                    "errorMessage",
                    "Enter a numeric shipment ID, for example 15."
            );

        } catch (HttpClientErrorException.NotFound ex) {
            model.addAttribute(
                    "errorMessage",
                    "Shipment not found. Check the ID and try again."
            );

        } catch (RestClientException | IllegalStateException ex) {
            model.addAttribute(
                    "errorMessage",
                    "Shipment information is temporarily unavailable."
            );
        }

        return "pages/tracking";
    }
}
