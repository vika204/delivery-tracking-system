package com.delivery.webui.exception;

import com.delivery.webui.controller.TrackingPageController;
import com.delivery.webui.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@ControllerAdvice(assignableTypes = TrackingPageController.class)
public class TrackingPageAdvice {

    private final boolean trackingStreamEnabled;

    public TrackingPageAdvice(@Value("${tracking.stream.enabled:false}") boolean trackingStreamEnabled) {
        this.trackingStreamEnabled = trackingStreamEnabled;
    }

    @ModelAttribute
    void pageAttributes(Model model, HttpServletRequest request) {
        String shipmentId = request.getParameter("shipmentId");
        model.addAttribute("pageTitle", "Track your delivery — WEB EXPRESS");
        model.addAttribute("activePage", "tracking");
        model.addAttribute("headerState", "CLIENT".equalsIgnoreCase(request.getParameter("previewRole"))
                ? "CLIENT"
                : AuthenticatedUser.currentHeaderState());
        model.addAttribute("trackingStreamEnabled", trackingStreamEnabled);
        model.addAttribute("trackingInput", shipmentId == null ? "" : shipmentId.trim());
    }

    @ExceptionHandler(ResponseStatusException.class)
    String invalidRequest(ResponseStatusException ex, Model model,
                          HttpServletRequest request, HttpServletResponse response) {
        response.setStatus(ex.getStatusCode().value());
        return errorPage(model, request, ex.getReason());
    }

    @ExceptionHandler(HttpClientErrorException.NotFound.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    String shipmentNotFound(Model model, HttpServletRequest request) {
        return errorPage(model, request, "Shipment not found. Check the ID and try again.");
    }

    @ExceptionHandler({RestClientException.class, IllegalStateException.class})
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    String trackingUnavailable(Model model, HttpServletRequest request) {
        return errorPage(model, request, "Shipment information is temporarily unavailable.");
    }

    private String errorPage(Model model, HttpServletRequest request, String message) {
        pageAttributes(model, request);
        model.addAttribute("errorMessage", message);
        return "pages/tracking";
    }
}
